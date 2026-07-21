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
import io.softa.starter.user.service.PermissionCacheInvalidator;
import io.softa.starter.user.service.RoleDataScopeService;
import io.softa.starter.user.service.RoleNavigationService;
import io.softa.starter.user.service.RoleSensitiveFieldSetService;
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
 * Then evicts every affected role's users' {@code perm:} snapshots.
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
    private final PermissionCacheInvalidator cacheInvalidator;

    public EntitlementRoleCleanupService(RoleNavigationService roleNavigationService,
                                         RoleDataScopeService roleDataScopeService,
                                         RoleSensitiveFieldSetService roleSensitiveFieldSetService,
                                         PermissionCacheInvalidator cacheInvalidator) {
        this.roleNavigationService = roleNavigationService;
        this.roleDataScopeService = roleDataScopeService;
        this.roleSensitiveFieldSetService = roleSensitiveFieldSetService;
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
        // Second pass + eviction. A role the downgrade stripped of its LAST nav grant now grants no
        // access → its model-keyed scope/sensitive rows are orphaned; delete them (safe — removing a
        // no-access role's rows can only shrink the per-model union, never widen). Roles that still
        // have navs keep their grants. Every affected role's snapshot is evicted regardless.
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
            cacheInvalidator.evictByRole(tenantId, roleId);
        }
        if (removed > 0 || scopeRemoved > 0 || sfsRemoved > 0) {
            log.info("entitlement cleanup — tenant {} removed {} nav grant(s), {} data-scope + "
                            + "{} sensitive-field grant(s) (from fully-stripped roles) across {} role(s)",
                    tenantId, removed, scopeRemoved, sfsRemoved, affectedRoles.size());
        }
        return removed;
    }
}
