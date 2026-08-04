package io.softa.starter.user.entitlement;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** {@link EntitlementRoleCleanupService} — removes nav grants whose module isn't entitled, purges the
 *  scope/sensitive grants of any role the downgrade left with no nav, and evicts the whole tenant's
 *  snapshots (entitledModules rides in every one of them, so per-role eviction would miss upgrades
 *  and the runtime-computed TENANT_ADMIN). */
class EntitlementRoleCleanupServiceTest {

    private static final long TENANT = 10L;

    private RoleNavigationService roleNavigationService;
    private RoleDataScopeService roleDataScopeService;
    private RoleSensitiveFieldSetService roleSensitiveFieldSetService;
    private UserAccountService userAccountService;
    private PermissionCacheInvalidator cacheInvalidator;
    private EntitlementRoleCleanupService service;

    @BeforeEach
    void setUp() {
        roleNavigationService = mock(RoleNavigationService.class);
        roleDataScopeService = mock(RoleDataScopeService.class);
        roleSensitiveFieldSetService = mock(RoleSensitiveFieldSetService.class);
        userAccountService = mock(UserAccountService.class);
        cacheInvalidator = mock(PermissionCacheInvalidator.class);
        service = new EntitlementRoleCleanupService(
                roleNavigationService, roleDataScopeService, roleSensitiveFieldSetService,
                userAccountService, cacheInvalidator);
        when(userAccountService.searchList(any(Filters.class)))
                .thenReturn(List.of(account(1_001L), account(1_002L)));
    }

    private static UserAccount account(long id) {
        UserAccount a = new UserAccount();
        a.setId(id);
        a.setTenantId(TENANT);
        return a;
    }

    private static RoleNavigation grant(long id, long roleId, String navId) {
        RoleNavigation g = new RoleNavigation();
        g.setId(id);
        g.setTenantId(TENANT);
        g.setRoleId(roleId);
        g.setNavigationId(navId);
        return g;
    }

    private static RoleDataScope scope(long id, long roleId, String model) {
        RoleDataScope s = new RoleDataScope();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setRoleId(roleId);
        s.setModel(model);
        return s;
    }

    private static RoleSensitiveFieldSet sfs(long id, long roleId, String sfsId) {
        RoleSensitiveFieldSet s = new RoleSensitiveFieldSet();
        s.setId(id);
        s.setTenantId(TENANT);
        s.setRoleId(roleId);
        s.setSensitiveFieldSetId(sfsId);
        return s;
    }

    @Test
    void removesOverPlanGrants_evictsWholeTenant() {
        RoleNavigation keep = grant(1L, 100L, "navigation.core-hr.employee.list");    // entitled
        RoleNavigation dropAi = grant(2L, 100L, "navigation.ai.chat");                 // not entitled
        RoleNavigation dropAtt = grant(3L, 200L, "navigation.attendance.calendar");    // not entitled
        when(roleNavigationService.searchList(any(Filters.class)))
                .thenReturn(List.of(keep, dropAi, dropAtt));

        int removed = service.cleanup(TENANT, Set.of("core-hr", "users", "system"));

        assertThat(removed).isEqualTo(2);
        verify(roleNavigationService).deleteById(2L);
        verify(roleNavigationService).deleteById(3L);
        verify(roleNavigationService, never()).deleteById(1L);           // entitled grant kept
        verify(cacheInvalidator).evictBatch(TENANT, Set.of(1_001L, 1_002L));   // every user, not just 100/200
    }

    @Test
    void allEntitled_noRemovalButStillEvicts() {
        when(roleNavigationService.searchList(any(Filters.class))).thenReturn(List.of(
                grant(1L, 100L, "navigation.core-hr.x"),
                grant(2L, 100L, "navigation.users.y")));

        int removed = service.cleanup(TENANT, Set.of("core-hr", "users"));

        assertThat(removed).isZero();
        verify(roleNavigationService, never()).deleteById(any());
        // Still evicted: an upgrade removes no grant, yet every snapshot's entitledModules is stale.
        verify(cacheInvalidator).evictBatch(TENANT, Set.of(1_001L, 1_002L));
    }

    @Test
    void strippedRole_purgesScopeAndSensitive() {
        // Role 200's only nav is de-entitled → after removal it has zero navs (fully stripped),
        // so its model-keyed scope + sensitive grants are orphaned and must be deleted.
        when(roleNavigationService.searchList(any(Filters.class)))
                .thenReturn(List.of(grant(3L, 200L, "navigation.ai.chat")));
        when(roleNavigationService.count(any(Filters.class))).thenReturn(0L);
        when(roleDataScopeService.searchList(any(Filters.class)))
                .thenReturn(List.of(scope(50L, 200L, "Employee")));
        when(roleSensitiveFieldSetService.searchList(any(Filters.class)))
                .thenReturn(List.of(sfs(60L, 200L, "emp-comp")));

        service.cleanup(TENANT, Set.of("core-hr"));

        verify(roleDataScopeService).deleteByIds(List.of(50L));
        verify(roleSensitiveFieldSetService).deleteByIds(List.of(60L));
        verify(cacheInvalidator).evictBatch(TENANT, Set.of(1_001L, 1_002L));
    }

    @Test
    void survivingRole_keepsScopeAndSensitive() {
        // Role 100 loses its ai nav but keeps core-hr → still has a nav → its scope/sensitive stay.
        when(roleNavigationService.searchList(any(Filters.class))).thenReturn(List.of(
                grant(1L, 100L, "navigation.core-hr.employee.list"),
                grant(2L, 100L, "navigation.ai.chat")));
        when(roleNavigationService.count(any(Filters.class))).thenReturn(1L);

        service.cleanup(TENANT, Set.of("core-hr"));

        verify(roleNavigationService).deleteById(2L);
        verify(roleDataScopeService, never()).deleteByIds(anyList());
        verify(roleSensitiveFieldSetService, never()).deleteByIds(anyList());
        verify(cacheInvalidator).evictBatch(TENANT, Set.of(1_001L, 1_002L));
    }

    @Test
    void nullTenant_isNoOp() {
        assertThat(service.cleanup(null, Set.of("core-hr"))).isZero();
        verify(roleNavigationService, never()).searchList(any(Filters.class));
    }
}
