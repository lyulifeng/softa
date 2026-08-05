package io.softa.starter.user.controller;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.constant.RoleConstant;
import io.softa.starter.user.service.PermissionCacheInvalidator;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserRoleRelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests the roles-eviction shadow on {@link UserAccountController}. Editing the
 * {@code roles} ManyToMany via a UserAccount update cascades into
 * {@code user_role_rel} through the generic ORM write, which does NOT publish
 * {@code UserRoleRelChangedEvent} — so the controller must evict that user's
 * cached PermissionInfo itself, and ONLY when the payload actually touched
 * roles.
 *
 * <p>{@code IdUtils.formatMapId} is a static that consults model metadata (not
 * loaded in a unit test), so it is mocked to a no-op; the {@code @DataMask}
 * aspect is inert when the method is invoked directly (no Spring proxy).
 */
@SuppressWarnings({"rawtypes", "unchecked"})
class UserAccountControllerTest {

    private UserAccountController controller;
    private ModelService modelService;
    private PermissionCacheInvalidator invalidator;

    /** {@code SystemConfig.env} is a @PostConstruct singleton; a plain unit test has to supply it. */
    private SystemConfig previousEnv;

    @AfterEach
    void restoreEnv() {
        SystemConfig.env = previousEnv;   // a static — do not leak it into the next test class
    }

    @BeforeEach
    void setUp() {
        previousEnv = SystemConfig.env;
        SystemConfig env = new SystemConfig();
        env.setEnableMultiTenancy(true);
        SystemConfig.env = env;
        controller = new UserAccountController();
        modelService = mock(ModelService.class);
        invalidator = mock(PermissionCacheInvalidator.class);
        ReflectionTestUtils.setField(controller, "modelService", modelService);
        ReflectionTestUtils.setField(controller, "permissionCacheInvalidator", invalidator);
    }

    private static Map<String, Object> row(Object id, boolean withRoles) {
        Map<String, Object> row = new HashMap<>();
        row.put("id", id);
        row.put("nickname", "Alice");
        if (withRoles) row.put("roles", List.of("r1", "r2"));
        return row;
    }

    @Test
    void updateOne_rolesChanged_evictsThatUser() {
        when(modelService.updateOne(eq("UserAccount"), any())).thenReturn(true);

        try (MockedStatic<IdUtils> ignored = Mockito.mockStatic(IdUtils.class)) {
            inTenant(9L, () -> controller.updateOne(row(7L, true)));
        }

        verify(invalidator).evictBatch(9L, Set.of(7L));
    }

    @Test
    void updateOne_stringId_coercedAndEvicted() {
        when(modelService.updateOne(eq("UserAccount"), any())).thenReturn(true);

        try (MockedStatic<IdUtils> ignored = Mockito.mockStatic(IdUtils.class)) {
            inTenant(9L, () -> controller.updateOne(row("7", true)));
        }

        verify(invalidator).evictBatch(9L, Set.of(7L));
    }

    @Test
    void updateOne_noRolesInPayload_doesNotEvict() {
        when(modelService.updateOne(eq("UserAccount"), any())).thenReturn(true);

        try (MockedStatic<IdUtils> ignored = Mockito.mockStatic(IdUtils.class)) {
            inTenant(9L, () -> controller.updateOne(row(7L, false)));
        }

        verify(invalidator, never()).evictBatch(any(), any());
    }

    @Test
    void updateOneAndFetch_rolesChanged_evictsThatUser() {
        when(modelService.updateOneAndFetch(eq("UserAccount"), any(), any()))
                .thenReturn(new HashMap<>());

        try (MockedStatic<IdUtils> ignored = Mockito.mockStatic(IdUtils.class)) {
            inTenant(9L, () -> controller.updateOneAndFetch(row(7L, true)));
        }

        verify(invalidator).evictBatch(9L, Set.of(7L));
    }

    private static void inTenant(Long tenantId, Runnable action) {
        Context ctx = new Context();
        ctx.setTenantId(tenantId);
        ContextHolder.runWith(ctx, action);
    }

    // ─── the roster's cross-tenant window ───

    /**
     * The super-admin's account list spans tenants by definition (every tenant's admins, plus its own
     * tenant's users), so these reads run inside a deliberate cross-tenant window. It has to be
     * exactly that narrow: opened only for the super-admin, and only around this read. The check is
     * on the context the search observes, because that is what the ORM consults when it decides
     * whether to append {@code tenant_id = ?}.
     */
    private Boolean crossTenantSeenBySearch(Set<String> roleCodes) {
        AtomicReference<Boolean> seen = new AtomicReference<>();
        when(modelService.searchList(eq("UserAccount"), any())).thenAnswer(inv -> {
            seen.set(ContextHolder.getContext().isCrossTenant());
            return List.of();
        });
        Context ctx = new Context();
        ctx.setTenantId(9L);
        ctx.setRoleCodes(roleCodes);
        ContextHolder.runWith(ctx, () -> controller.searchList(null));
        return seen.get();
    }

    @Test
    void searchList_superAdmin_readsInsideACrossTenantWindow() {
        ReflectionTestUtils.setField(controller, "roleService", roleServiceReturningNoAdminRoles());
        ReflectionTestUtils.setField(controller, "userRoleRelService", mock(UserRoleRelService.class));

        assertThat(crossTenantSeenBySearch(Set.of(RoleConstant.CODE_SUPER_ADMIN))).isTrue();
    }

    @Test
    void searchList_tenantAdmin_staysTenantIsolated() {
        // A TENANT_ADMIN is the whole point of the distinction: it administers users, but only its
        // own tenant's. If the window keyed off "is an admin" rather than "is THE platform admin",
        // this is the test that would fail.
        assertThat(crossTenantSeenBySearch(Set.of(RoleConstant.CODE_TENANT_ADMIN))).isFalse();
    }

    @Test
    void searchList_ordinaryUser_staysTenantIsolated() {
        assertThat(crossTenantSeenBySearch(Set.of("HR"))).isFalse();
    }

    @Test
    void searchList_leavesTheOuterContextUntouched() {
        // The window is scoped to the read. A leak would hand the rest of the request — including any
        // write — an un-isolated context.
        ReflectionTestUtils.setField(controller, "roleService", roleServiceReturningNoAdminRoles());
        ReflectionTestUtils.setField(controller, "userRoleRelService", mock(UserRoleRelService.class));
        when(modelService.searchList(eq("UserAccount"), any())).thenReturn(List.of());

        Context ctx = new Context();
        ctx.setTenantId(9L);
        ctx.setRoleCodes(Set.of(RoleConstant.CODE_SUPER_ADMIN));
        ContextHolder.runWith(ctx, () -> controller.searchList(null));

        assertThat(ctx.isCrossTenant()).isFalse();
    }

    private static RoleService roleServiceReturningNoAdminRoles() {
        RoleService roleService = mock(RoleService.class);
        when(roleService.searchList(any(Filters.class))).thenReturn(List.of());
        return roleService;
    }
}
