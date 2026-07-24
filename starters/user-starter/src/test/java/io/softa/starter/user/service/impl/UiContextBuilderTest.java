package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.EntitlementService;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.user.dto.EffectiveAccess;
import io.softa.starter.user.dto.UiContext;
import io.softa.starter.user.entity.Navigation;
import io.softa.starter.user.entity.Role;
import io.softa.starter.user.entity.RoleNavigation;
import io.softa.starter.user.entity.RoleSensitiveFieldSet;
import io.softa.starter.user.entity.UserRoleRel;
import io.softa.starter.user.enums.NavigationType;
import io.softa.starter.user.service.NavigationModelResolver;
import io.softa.starter.user.service.RoleNavigationService;
import io.softa.starter.user.service.RoleSensitiveFieldSetService;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserRoleRelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the cache-miss fallback builder. Verifies it reproduces the
 * engine snapshot's FE-facing shape (roleCodes / navigations / permissions /
 * modelSensitiveFieldSetsMap) from user-starter's own entities,
 * including ancestor expansion and the SUPER_ADMIN / no-role empty-grants case.
 */
class UiContextBuilderTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private UserRoleRelService userRoleRelService;
    private RoleService roleService;
    private RoleNavigationService roleNavigationService;
    private RoleSensitiveFieldSetService roleSensitiveFieldSetService;
    private NavigationModelResolver navigationModelResolver;
    private ModelService<?> modelService;
    private UiContextBuilder builder;

    @BeforeEach
    void setUp() {
        userRoleRelService = mock(UserRoleRelService.class);
        roleService = mock(RoleService.class);
        roleNavigationService = mock(RoleNavigationService.class);
        roleSensitiveFieldSetService = mock(RoleSensitiveFieldSetService.class);
        navigationModelResolver = mock(NavigationModelResolver.class);
        modelService = mock(ModelService.class);

        // Nav tree: hr → hr.employee (leaf). Ancestor index built from this.
        when(navigationModelResolver.allNavigations()).thenReturn(List.of(
                nav("hr", null, NavigationType.GROUP),
                nav("hr.employee", "hr", NavigationType.MENU)));

        builder = new UiContextBuilder(userRoleRelService, roleService, roleNavigationService,
                roleSensitiveFieldSetService, navigationModelResolver, modelService);
        ReflectionTestUtils.invokeMethod(builder, "initAncestorIndex");
    }

    @Test
    void noRoles_emptyGrants_notSuperAdmin() {
        when(userRoleRelService.searchList(any(FlexQuery.class))).thenReturn(List.of());

        UiContext out = builder.build(42L);

        assertThat(out.getRoleCodes()).isEmpty();
        assertThat(out.getNavigations()).isEmpty();
        assertThat(out.getPermissions()).isEmpty();
    }

    @Test
    void superAdmin_emptyGrantsButFlagged() {
        when(userRoleRelService.searchList(any(FlexQuery.class))).thenReturn(List.of(rel(42L, 1L)));
        when(roleService.searchList(any(FlexQuery.class))).thenReturn(List.of(role(1L, "SUPER_ADMIN")));

        UiContext out = builder.build(42L);

        // Super-admin is carried by roleCodes (the FE derives it); grants stay empty.
        assertThat(out.getRoleCodes()).containsExactly("SUPER_ADMIN");
        assertThat(out.getNavigations()).isEmpty();
        assertThat(out.getPermissions()).isEmpty();
    }

    @Test
    void normalUser_navsWithAncestors_permissions_andSfsMap() {
        when(userRoleRelService.searchList(any(FlexQuery.class))).thenReturn(List.of(rel(42L, 100L)));
        when(roleService.searchList(any(FlexQuery.class))).thenReturn(List.of(role(100L, "HR")));
        when(roleNavigationService.searchList(any(FlexQuery.class)))
                .thenReturn(List.of(roleNav("hr.employee", "employee.view", "employee.create")));
        when(roleSensitiveFieldSetService.searchList(any(FlexQuery.class)))
                .thenReturn(List.of(roleSfs("comp")));
        when(modelService.searchList(eq("SensitiveFieldSet"), any(FlexQuery.class)))
                .thenReturn(List.of(Map.of("id", "comp", "model", "Employee")));

        UiContext out = builder.build(42L);

        // Leaf grant expands to include the ancestor "hr".
        assertThat(out.getNavigations()).containsExactlyInAnyOrder("hr.employee", "hr");
        assertThat(out.getPermissions()).containsExactlyInAnyOrder("employee.view", "employee.create");
        // SFS grouped under its canonical model.
        assertThat(out.getModelSensitiveFieldSetsMap().get("Employee")).containsExactly("comp");
    }

    @Test
    void effectiveAccess_nonAdminRole_computedFalse() {
        EffectiveAccess access = builder.effectiveAccessForRole("HR", 1L);

        assertThat(access.isComputed()).isFalse();
        assertThat(access.getRoleCode()).isNull();
        assertThat(access.getNavigations()).isNull();
        assertThat(access.getPermissions()).isNull();
        assertThat(access.getDataScopeAll()).isNull();
        assertThat(access.getSensitiveAll()).isNull();
    }

    @Test
    void effectiveAccess_superAdmin_everyNavAndPermission() {
        // Default nav tree (hr, hr.employee); permissions mapped to those navs.
        when(modelService.searchList(eq("Permission"), any(FlexQuery.class))).thenReturn(List.of(
                Map.of("id", "employee.view", "navigationId", "hr.employee"),
                Map.of("id", "hr.open", "navigationId", "hr")));

        EffectiveAccess access = builder.effectiveAccessForRole("SUPER_ADMIN", 99L);

        assertThat(access.isComputed()).isTrue();
        assertThat(access.getRoleCode()).isEqualTo("SUPER_ADMIN");
        // SUPER_ADMIN keeps every nav (platform included) + those navs' permissions.
        assertThat(access.getNavigations()).containsExactlyInAnyOrder("hr", "hr.employee");
        assertThat(access.getPermissions()).containsExactlyInAnyOrder("employee.view", "hr.open");
        assertThat(access.getDataScopeAll()).isTrue();
        assertThat(access.getSensitiveAll()).isTrue();
    }

    @Test
    void effectiveAccess_tenantAdmin_dropsPlatformOnlyNavs() {
        ReflectionTestUtils.setField(builder, "platformNavPrefixesCsv", "navigation.system.");
        when(navigationModelResolver.allNavigations()).thenReturn(List.of(
                nav("core-hr.employee", null, NavigationType.MENU),
                nav("navigation.system.audit", null, NavigationType.MENU)));
        when(modelService.searchList(eq("Permission"), any(FlexQuery.class))).thenReturn(List.of(
                Map.of("id", "employee.view", "navigationId", "core-hr.employee"),
                Map.of("id", "audit.view", "navigationId", "navigation.system.audit")));

        EffectiveAccess access = builder.effectiveAccessForRole("TENANT_ADMIN", 7L);

        assertThat(access.isComputed()).isTrue();
        assertThat(access.getRoleCode()).isEqualTo("TENANT_ADMIN");
        // Platform-only nav (+ its permission) dropped; tenant-facing nav kept.
        assertThat(access.getNavigations()).containsExactly("core-hr.employee");
        assertThat(access.getPermissions()).containsExactly("employee.view");
        assertThat(access.getDataScopeAll()).isTrue();
        assertThat(access.getSensitiveAll()).isTrue();
    }

    @Test
    void effectiveAccess_tenantAdmin_narrowedByPlanEntitlement() {
        // Entitlement gate installed → only the "core-hr" module is entitled.
        EntitlementService gate = mock(EntitlementService.class);
        when(gate.entitledModules(7L)).thenReturn(Set.of("core-hr"));
        ReflectionTestUtils.setField(builder, "entitlementService", gate);
        when(navigationModelResolver.allNavigations()).thenReturn(List.of(
                nav("core-hr.employee", null, NavigationType.MENU),
                nav("ai.chat", null, NavigationType.MENU)));
        when(modelService.searchList(eq("Permission"), any(FlexQuery.class))).thenReturn(List.of(
                Map.of("id", "employee.view", "navigationId", "core-hr.employee"),
                Map.of("id", "ai.use", "navigationId", "ai.chat")));

        EffectiveAccess access = builder.effectiveAccessForRole("TENANT_ADMIN", 7L);

        // "ai" module not entitled → its nav + permission dropped; "core-hr" kept.
        assertThat(access.getNavigations()).containsExactly("core-hr.employee");
        assertThat(access.getPermissions()).containsExactly("employee.view");
    }

    // ─── helpers ───

    private static Navigation nav(String id, String parentId, NavigationType type) {
        Navigation n = new Navigation();
        n.setId(id);
        n.setParentId(parentId);
        n.setType(type);
        return n;
    }

    private static UserRoleRel rel(Long userId, Long roleId) {
        UserRoleRel r = new UserRoleRel();
        r.setUserId(userId);
        r.setRoleId(roleId);
        return r;
    }

    private static Role role(Long id, String code) {
        Role r = new Role();
        r.setId(id);
        r.setCode(code);
        r.setActive(true);
        return r;
    }

    private static RoleNavigation roleNav(String navId, String... permIds) {
        RoleNavigation rn = new RoleNavigation();
        rn.setNavigationId(navId);
        ArrayNode arr = JSON.arrayNode();
        for (String p : permIds) arr.add(p);
        rn.setPermissionIds(arr);
        return rn;
    }

    private static RoleSensitiveFieldSet roleSfs(String sfsId) {
        RoleSensitiveFieldSet g = new RoleSensitiveFieldSet();
        g.setSensitiveFieldSetId(sfsId);
        return g;
    }
}
