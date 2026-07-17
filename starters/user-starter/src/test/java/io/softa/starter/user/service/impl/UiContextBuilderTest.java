package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
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
 * engine snapshot's FE-facing shape (roleCodes / superAdmin / navigations /
 * permissions / modelSensitiveFieldSetsMap) from user-starter's own entities,
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

        JsonNode out = builder.build(42L);

        assertThat(out.get("superAdmin").asBoolean()).isFalse();
        assertThat(codes(out.get("navigations"))).isEmpty();
        assertThat(codes(out.get("permissions"))).isEmpty();
        assertThat(codes(out.get("roleCodes"))).isEmpty();
    }

    @Test
    void superAdmin_emptyGrantsButFlagged() {
        when(userRoleRelService.searchList(any(FlexQuery.class))).thenReturn(List.of(rel(42L, 1L)));
        when(roleService.searchList(any(FlexQuery.class))).thenReturn(List.of(role(1L, "SUPER_ADMIN")));

        JsonNode out = builder.build(42L);

        assertThat(out.get("superAdmin").asBoolean()).isTrue();
        assertThat(codes(out.get("roleCodes"))).containsExactly("SUPER_ADMIN");
        // Empty grants — downstream detects super-admin by roleCodes.
        assertThat(codes(out.get("navigations"))).isEmpty();
        assertThat(codes(out.get("permissions"))).isEmpty();
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

        JsonNode out = builder.build(42L);

        // Leaf grant expands to include the ancestor "hr".
        assertThat(codes(out.get("navigations"))).containsExactlyInAnyOrder("hr.employee", "hr");
        assertThat(codes(out.get("permissions"))).containsExactlyInAnyOrder("employee.view", "employee.create");
        // SFS grouped under its canonical model.
        assertThat(codes(out.get("modelSensitiveFieldSetsMap").get("Employee"))).containsExactly("comp");
        assertThat(out.get("superAdmin").asBoolean()).isFalse();
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

    private static List<String> codes(JsonNode arr) {
        List<String> out = new java.util.ArrayList<>();
        if (arr != null) {
            for (JsonNode n : arr) out.add(n.asString());
        }
        return out;
    }
}
