package io.softa.starter.user.entitlement;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Service;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.RoleDataScope;
import io.softa.starter.user.entity.RoleNavigation;
import io.softa.starter.user.entity.RoleSensitiveFieldSet;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.service.PermissionCacheInvalidator;
import io.softa.starter.user.service.RoleDataScopeService;
import io.softa.starter.user.service.RoleNavigationService;
import io.softa.starter.user.service.RoleSensitiveFieldSetService;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.util.NavIds;

/**
 * Physically removes a tenant's over-plan role grants after a downgrade (版本计费 v2 §2.2 —
 * 2026-07-14 decision: hard clean, re-upgrade = admin re-selects, no auto-restore). Two passes:
 * <ol>
 *   <li><b>Nav grants</b> — delete each {@code role_navigation} grant whose navigation module is no
 *       longer entitled (nav ids carry their module, so this is module-precise).</li>
 *   <li><b>Orphaned scope / sensitive grants</b> — for any role the downgrade left with <b>zero</b>
 *       remaining nav grants, delete its {@code role_data_scope} + {@code role_sensitive_field_set}
 *       rows too. These are keyed by <i>model</i> (not module), so they can't be filtered by the
 *       dropped module — but a role with no navs grants no access, so its scope/sensitive rows are
 *       pure orphans. Leaving them lets a still-entitled role's access to a <i>shared</i> model union
 *       in this role's stale (possibly broad / ALL) row-scope → widened rows, and its stale
 *       sensitive-set grants → fewer masked fields. Deleting a <b>fully-stripped</b> role's rows can
 *       only shrink that per-model union (never widen), so it is safe. A role that still has navs is
 *       deliberately left untouched: deleting a surviving role's narrow scope would itself widen it
 *       to unrestricted, and its grants may still apply to models it can reach (directly or as a
 *       related read) — those stay the admin's to re-tighten (matching no-auto-restore).</li>
 * </ol>
 * Then evicts <b>every user in the tenant</b>'s {@code perm:} snapshot — not just the users of the
 * roles this cleanup touched. The snapshot carries the tenant's {@code entitledModules} (the FE
 * narrows the sidebar with it), so a plan change invalidates every user's snapshot even when no
 * grant moved: an <i>upgrade</i> strips nothing, and {@code TENANT_ADMIN} is runtime-computed with
 * no static nav grants at all, so it never appears in {@code affectedRoles}. Evicting only the
 * affected roles left tenant admins on their pre-change module set until the 1h TTL — two admins of
 * the same tenant could see different modules depending on when each snapshot was built.
 *
 * <p>Lives in user-starter (owns the RBAC grants); driven by {@code EntitlementCleanupConsumer}
 * off the entitlement-change MQ message — no dependency on tenant-starter (the message DTO is a
 * framework type). {@link SkipPermissionCheck}: this is a system cleanup, not a user query.
 */
@Slf4j
@Service
public class EntitlementRoleCleanupService {

    private final RoleNavigationService roleNavigationService;
    private final RoleDataScopeService roleDataScopeService;
    private final RoleSensitiveFieldSetService roleSensitiveFieldSetService;
    private final UserAccountService userAccountService;
    private final PermissionCacheInvalidator cacheInvalidator;

    public EntitlementRoleCleanupService(RoleNavigationService roleNavigationService,
                                         RoleDataScopeService roleDataScopeService,
                                         RoleSensitiveFieldSetService roleSensitiveFieldSetService,
                                         UserAccountService userAccountService,
                                         PermissionCacheInvalidator cacheInvalidator) {
        this.roleNavigationService = roleNavigationService;
        this.roleDataScopeService = roleDataScopeService;
        this.roleSensitiveFieldSetService = roleSensitiveFieldSetService;
        this.userAccountService = userAccountService;
        this.cacheInvalidator = cacheInvalidator;
    }

    /**
     * @return the number of nav grants removed.
     */
    @SkipPermissionCheck
    public int cleanup(Long tenantId, Set<String> entitledModules) {
        if (tenantId == null || entitledModules == null) {
            return 0;
        }
        List<RoleNavigation> grants = roleNavigationService.searchList(
                Filters.of("tenantId", Operator.EQUAL, tenantId));
        Set<Long> affectedRoles = new HashSet<>();
        int removed = 0;
        for (RoleNavigation grant : grants) {
            String module = NavIds.moduleOf(grant.getNavigationId());
            if (module != null && !entitledModules.contains(module)) {
                roleNavigationService.deleteById(grant.getId());
                if (grant.getRoleId() != null) {
                    affectedRoles.add(grant.getRoleId());
                }
                removed++;
            }
        }
        // Second pass. A role the downgrade stripped of its LAST nav grant now grants no
        // access → its model-keyed scope/sensitive rows are orphaned; delete them (safe — removing a
        // no-access role's rows can only shrink the per-model union, never widen). Roles that still
        // have navs keep their grants.
        int scopeRemoved = 0;
        int sfsRemoved = 0;
        for (Long roleId : affectedRoles) {
            if (roleNavigationService.count(new Filters().eq(RoleNavigation::getRoleId, roleId)) == 0) {
                List<Long> scopeIds = roleDataScopeService
                        .searchList(new Filters().eq(RoleDataScope::getRoleId, roleId))
                        .stream().map(RoleDataScope::getId).toList();
                if (!scopeIds.isEmpty()) {
                    roleDataScopeService.deleteByIds(scopeIds);
                    scopeRemoved += scopeIds.size();
                }
                List<Long> sfsIds = roleSensitiveFieldSetService
                        .searchList(new Filters().eq(RoleSensitiveFieldSet::getRoleId, roleId))
                        .stream().map(RoleSensitiveFieldSet::getId).toList();
                if (!sfsIds.isEmpty()) {
                    roleSensitiveFieldSetService.deleteByIds(sfsIds);
                    sfsRemoved += sfsIds.size();
                }
            }
        }
        // Evict the whole tenant, not just `affectedRoles` — see the class javadoc: entitledModules
        // rides in every user's snapshot, so an upgrade (which strips nothing) and TENANT_ADMIN
        // (runtime-computed, never in affectedRoles) both need invalidating too.
        int evicted = evictTenant(tenantId);
        if (removed > 0 || scopeRemoved > 0 || sfsRemoved > 0) {
            log.info("entitlement cleanup — tenant {} removed {} nav grant(s), {} data-scope + "
                            + "{} sensitive-field grant(s) (from fully-stripped roles) across {} role(s)",
                    tenantId, removed, scopeRemoved, sfsRemoved, affectedRoles.size());
        }
        log.debug("entitlement cleanup — tenant {} evicted {} user snapshot(s)", tenantId, evicted);
        return removed;
    }

    /**
     * Evict every account in the tenant. {@link PermissionCacheInvalidator} deliberately exposes no
     * tenant-wide entry point, so the caller enumerates the ids and goes through
     * {@code evictBatch}. Accounts — not {@code user_role_rel} — because a snapshot exists for any
     * user who has logged in, whether or not they hold a role.
     *
     * @return the number of accounts whose snapshot key was cleared.
     */
    private int evictTenant(Long tenantId) {
        List<UserAccount> accounts = userAccountService.searchList(
                Filters.of("tenantId", Operator.EQUAL, tenantId));
        Set<Long> userIds = new HashSet<>(accounts.size());
        for (UserAccount account : accounts) {
            if (account.getId() != null) {
                userIds.add(account.getId());
            }
        }
        if (userIds.isEmpty()) {
            return 0;
        }
        cacheInvalidator.evictBatch(tenantId, userIds);
        return userIds.size();
    }
}
