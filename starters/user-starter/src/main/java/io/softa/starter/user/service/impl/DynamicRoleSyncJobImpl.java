package io.softa.starter.user.service.impl;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.user.entity.Role;
import io.softa.starter.user.entity.UserRoleRel;
import io.softa.starter.user.enums.RoleSource;
import io.softa.starter.user.service.DynamicRoleSyncJob;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserRoleRelService;
import io.softa.starter.user.util.ModelRefIds;

/**
 * DynamicRoleSyncJob — single source of truth for syncing the DYNAMIC rows
 * in user_role. Callers:
 *   1. RoleController wizard save (inline, per-role) — admins see synced
 *      members on the detail page immediately.
 *   2. {@link #syncAll()} via a {@code sys_cron} row + Pulsar consumer in
 *      the assembly module (default name: {@code "DynamicRoleSync"}) —
 *      tenant-wide rescan that catches employee data changes between role
 *      saves.
 *   3. {@link #syncMembershipForUser} — per-user re-evaluation, invoked
 *      by domain-specific bridges when they see an HR-side change that
 *      might shift dynamic-role membership (e.g. an
 *      {@code HrEventBridge} listening on the HR module's own
 *      {@code EmployeeChangedEvent}). The framework knows nothing about
 *      HR events; the bridge translates its own domain event into this
 *      generic {@code (tenantId, userId)} call.
 *
 * MANUAL rows are never touched. Each pass:
 *   - DELETE WHERE role_id = R AND source = 'Dynamic'
 *   - SELECT employee.userId WHERE rule AND userId IS SET AND userId.status = 'Active'
 *   - INSERT one (userId, R, 'Dynamic') per match
 *
 * The same (user, role) can also have a MANUAL row — schema unique key is
 * (tenant, user, role, source). The two rows coexist; permission evaluation
 * dedupes by (user, role).
 */
@Slf4j
@Service
public class DynamicRoleSyncJobImpl implements DynamicRoleSyncJob {

    private final RoleService roleService;
    private final UserRoleRelService userRoleRelService;
    private final ModelService<?> modelService;
    /** Explicit tx boundary for {@link #syncRoleInternal} — see the method's
     *  Javadoc for why this doesn't rely on Spring's {@code @Transactional}
     *  proxy interception. Known-Issues Lat3.
     *
     *  <p>Built from the container's {@link PlatformTransactionManager}
     *  (Spring Boot auto-registers one; {@code TransactionTemplate} is not
     *  auto-registered so we build it here rather than expose an
     *  @{@code Bean} for one call site). */
    private final TransactionTemplate transactionTemplate;

    public DynamicRoleSyncJobImpl(
            RoleService roleService,
            UserRoleRelService userRoleRelService,
            ModelService<?> modelService,
            PlatformTransactionManager transactionManager) {
        this.roleService = roleService;
        this.userRoleRelService = userRoleRelService;
        this.modelService = modelService;
        this.transactionTemplate = new TransactionTemplate(transactionManager);
    }

    @Override
    @Transactional
    public int syncRole(Long tenantId, Long roleId) {
        if (roleId == null) return 0;
        Optional<Role> roleOpt = roleService.getById(roleId);
        if (roleOpt.isEmpty()) {
            log.warn("DynamicRoleSyncJob.syncRole — role {} not found", roleId);
            return 0;
        }
        return syncRoleInternal(roleOpt.get());
    }

    /**
     * Sync one role from an already-loaded entity — saves the redundant
     * getById when callers (controller / event listener) already have the
     * Role in hand.
     */
    private int syncRoleEntity(Role role) {
        return syncRoleInternal(role);
    }

    /**
     * Delete-then-insert the DYNAMIC {@code user_role} rows for one role.
     * Both operations MUST run in a single transaction — a partial write
     * (delete succeeds, insert fails) would leave the role with zero
     * DYNAMIC members until the next sync.
     *
     * <h3>Explicit {@code TransactionTemplate} boundary (Known-Issues Lat3)</h3>
     * Not annotated {@code @Transactional}: Spring's proxy-based AOP does
     * not intercept private methods, and {@code @Transactional} on public
     * callers ({@link #syncRole}, {@link #syncRoleEntity}) is fragile —
     * self-calls from other methods in this class (e.g. {@link #syncAll}
     * calling {@code syncRoleEntity}) bypass the proxy, so the "public
     * caller carries the annotation" pattern silently loses atomicity for
     * every self-call site. Historically this class relied on that pattern
     * and would have broken any time a new caller was added inside this
     * class.
     *
     * <p>{@link TransactionTemplate} runs the delete+insert unit inside a
     * transaction regardless of how the method was reached — self-call,
     * proxy call, inline invocation from {@link #syncAll}. If the outer
     * caller already holds an active transaction, the default REQUIRED
     * propagation reuses it (nested logical tx); no double-commit or
     * savepoint churn. Failures propagate as-is; the tx rolls back.
     */
    private int syncRoleInternal(Role role) {
        Integer result = transactionTemplate.execute(status -> syncRoleInternalUnsafe(role));
        return result == null ? 0 : result;
    }

    /** Inner body of {@link #syncRoleInternal}; must run inside the tx
     *  established by the caller. Doesn't manage its own tx boundary. */
    private int syncRoleInternalUnsafe(Role role) {
        // 1. Snapshot the manual user-id set BEFORE the delete so we can
        //    fold both look-ups into the same round-trip pattern (manual
        //    + dynamic share the same role filter; ordering of these two
        //    relative to the delete doesn't matter for either correctness
        //    or visibility). Manual takes precedence — skip users that
        //    already have a MANUAL row for this role. By application
        //    convention at most one row per (user, role) exists, so this
        //    keeps Manual grants intact and avoids the UK collision case.
        Set<Long> manualUserIds = userRoleRelService.searchList(
                new Filters().eq(UserRoleRel::getRoleId, role.getId())
                        .eq(UserRoleRel::getSource, RoleSource.MANUAL.getCode())
        ).stream().map(UserRoleRel::getUserId).collect(java.util.stream.Collectors.toSet());

        // 2. Wipe existing DYNAMIC rows. When admin clears the rule
        //    entirely, the wipe is the only effect; otherwise it makes
        //    the subsequent insert idempotent (no diff-and-merge gymnastics).
        userRoleRelService.deleteByFilters(
                new Filters().eq(UserRoleRel::getRoleId, role.getId())
                        .eq(UserRoleRel::getSource, RoleSource.DYNAMIC.getCode()));

        JsonNode rule = role.getDynamicFilter();
        if (rule == null || rule.isNull() || !rule.isArray()) return 0;

        Object filterList = JsonUtils.jsonNodeToObject(rule);
        if (!(filterList instanceof List<?> list)) return 0;
        Filters filters = Filters.of(list);
        if (filters == null) return 0;

        // 3. AND in the user-account safety clauses (same as wizard's live
        //    preview) — pure-employee rows or disabled accounts never get
        //    a dynamic grant.
        filters.and(Filters.of("userId", Operator.IS_SET, null))
                .and(Filters.of("userId.status", Operator.EQUAL, "Active"));

        List<Map<String, Object>> employees = modelService.searchList(
                "Employee", new FlexQuery(List.of("userId"), filters));

        Set<Long> userIds = new LinkedHashSet<>();
        for (Map<String, Object> emp : employees) {
            Long uid = ModelRefIds.extractLongId(emp.get("userId"));
            if (uid != null) userIds.add(uid);
        }
        if (userIds.isEmpty()) return 0;

        List<UserRoleRel> rows = new ArrayList<>();
        for (Long uid : userIds) {
            if (manualUserIds.contains(uid)) continue;
            UserRoleRel ur = new UserRoleRel();
            ur.setRoleId(role.getId());
            ur.setUserId(uid);
            ur.setSource(RoleSource.DYNAMIC);
            rows.add(ur);
        }
        if (!rows.isEmpty()) userRoleRelService.createList(rows);
        log.info("DynamicRoleSyncJob.syncRole — role {} now has {} DYNAMIC members (skipped {} that already have Manual)",
                role.getId(), rows.size(), userIds.size() - rows.size());
        return rows.size();
    }

    /**
     * Nightly cron entry — recompute DYNAMIC membership for every role
     * with a rule. Runs per-role: reads roles under caller tenant, then
     * for each one queries Employee under {@code dynamicFilter} and
     * rewrites {@code user_role} DYNAMIC rows.
     *
     * <h3>⚠️ DO NOT annotate {@code @CrossTenant}</h3>
     * <b>Known-Issues Lat1</b>: adding {@code @CrossTenant} looks
     * innocuous (cron typically wants "all tenants"), but the
     * {@link io.softa.framework.orm.aspect.TenantAspect#crossTenant}
     * side-effect also flips {@code skipPermissionCheck=true} on the
     * context — which propagates through the {@code applyPerRole} path
     * and lets {@code modelService.searchList("Employee", dynamicFilter)}
     * run WITHOUT the tenant filter. Consequence: a dynamic role in
     * tenant A whose rule happens to also match Employees in tenant B
     * would pull those foreign-tenant employees into tenant A's
     * {@code user_role_rel}, silently granting them tenant A's role
     * permissions the next time they log in.
     *
     * <p>If you ever need cross-tenant sync, do it explicitly:
     * <pre>{@code
     * for (Long tenantId : tenantInfoService.getActiveTenantIds()) {
     *     Context ctx = ContextHolder.cloneContext();
     *     ctx.setTenantId(tenantId);
     *     ctx.setCrossTenant(false);
     *     ContextHolder.callWith(ctx, dynamicRoleSyncJob::syncAll);
     * }
     * }</pre>
     * or annotate the caller with {@code @PerTenant}. Either preserves
     * per-tenant isolation of the Employee lookup.
     */
    @Override
    public void syncAll() {
        // Walk every role with a non-null dynamicFilter. Active flag is NOT
        // checked here — inactive roles still get their user_role rows kept
        // in sync; PermissionInfoEnricher skips inactive roles during ACL
        // evaluation.
        List<Role> roles = roleService.searchList(
                new Filters().isSet(Role::getDynamicFilter));
        log.info("DynamicRoleSyncJob.syncAll — {} role(s) with a dynamic rule", roles.size());
        int totalGrants = 0;
        for (Role role : roles) {
            try {
                totalGrants += syncRoleEntity(role);
            } catch (Exception e) {
                log.error("DynamicRoleSyncJob.syncAll — role {} sync failed; continuing", role.getId(), e);
            }
        }
        log.info("DynamicRoleSyncJob.syncAll — done, {} total DYNAMIC grants across all roles", totalGrants);
    }

    @Override
    @Transactional
    public int syncMembershipForUser(Long tenantId, Long userId) {
        if (userId == null) return 0;

        // Load every dynamic role scoped to this tenant. Cross-tenant roles
        // can't apply to this user — their tenantId scopes them out. When
        // tenantId is null we fall back to a global scan (bridge failed to
        // resolve tenant; the safe move is to check every role).
        Filters roleFilter = new Filters().isSet(Role::getDynamicFilter);
        if (tenantId != null) roleFilter.eq(Role::getTenantId, tenantId);
        List<Role> roles = roleService.searchList(roleFilter);
        if (roles.isEmpty()) return 0;

        int added = 0;
        int removed = 0;
        for (Role role : roles) {
            try {
                if (applyPerUser(role, userId)) added++;
                else removed += removeDynamicIfPresent(role.getId(), userId);
            } catch (Exception e) {
                log.error("DynamicRoleSyncJob.syncMembershipForUser — role {} eval failed for userId={}; continuing",
                        role.getId(), userId, e);
            }
        }
        log.info("DynamicRoleSyncJob.syncMembershipForUser — tenantId={}, userId={}, +{} / -{} dynamic grants across {} role(s)",
                tenantId, userId, added, removed, roles.size());
        return added + removed;
    }

    /**
     * Returns true iff the user currently satisfies this role's dynamic
     * filter (including the standard active-userId safety clauses). When
     * true, ensures a {@code (userId, roleId, DYNAMIC)} row exists — unless
     * the user already has a MANUAL row for this role (Manual takes
     * precedence).
     */
    private boolean applyPerUser(Role role, Long userId) {
        JsonNode rule = role.getDynamicFilter();
        if (rule == null || rule.isNull() || !rule.isArray()) return false;
        Object filterList = JsonUtils.jsonNodeToObject(rule);
        if (!(filterList instanceof List<?> list)) return false;
        Filters filters = Filters.of(list);
        if (filters == null) return false;
        // AND the safety clauses (userId set + Active status) plus the
        // single-user anchor — we're asking "does the Employee whose
        // userId matches this user satisfy the rule?".
        filters.and(Filters.of("userId", Operator.IS_SET, null))
                .and(Filters.of("userId.status", Operator.EQUAL, "Active"))
                .and(Filters.of("userId", Operator.EQUAL, userId));
        long matches = modelService.count("Employee", filters);
        if (matches <= 0) return false;

        // Manual row check — if one exists, the schema's unique key
        // (tenant_id, user_id, role_id, source) still permits a separate
        // Dynamic row, but the wizard convention is one row per (user,
        // role). Skip the Dynamic insert when Manual is already present.
        boolean manualExists = userRoleRelService.exist(
                new Filters().eq(UserRoleRel::getRoleId, role.getId())
                        .eq(UserRoleRel::getUserId, userId)
                        .eq(UserRoleRel::getSource, RoleSource.MANUAL.getCode()));
        if (manualExists) return true; // matches the filter; just don't add a duplicate Dynamic row

        boolean dynamicExists = userRoleRelService.exist(
                new Filters().eq(UserRoleRel::getRoleId, role.getId())
                        .eq(UserRoleRel::getUserId, userId)
                        .eq(UserRoleRel::getSource, RoleSource.DYNAMIC.getCode()));
        if (dynamicExists) return true;

        UserRoleRel row = new UserRoleRel();
        row.setRoleId(role.getId());
        row.setUserId(userId);
        row.setSource(RoleSource.DYNAMIC);
        userRoleRelService.createOne(row);
        return true;
    }

    /** Remove the {@code (userId, roleId, DYNAMIC)} row if one exists. Manual
     *  rows are intentionally left intact. Returns count actually deleted. */
    private int removeDynamicIfPresent(Long roleId, Long userId) {
        List<UserRoleRel> existing = userRoleRelService.searchList(
                new Filters().eq(UserRoleRel::getRoleId, roleId)
                        .eq(UserRoleRel::getUserId, userId)
                        .eq(UserRoleRel::getSource, RoleSource.DYNAMIC.getCode()));
        if (existing.isEmpty()) return 0;
        userRoleRelService.deleteByIds(existing.stream().map(UserRoleRel::getId).toList());
        return existing.size();
    }

}
