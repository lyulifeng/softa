package io.softa.starter.permission.interceptor;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.SystemRole;
import io.softa.framework.base.exception.ConfigurationException;
import io.softa.framework.base.exception.PermissionException;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.index.EndpointIndex;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PermissionInterceptorTest {

    private EndpointIndex endpointIndex;
    private PermissionSnapshotProvider snapshotProvider;
    private PermissionInterceptorProperties props;
    private PermissionInterceptor interceptor;

    @BeforeEach
    void setUp() {
        endpointIndex = mock(EndpointIndex.class);
        snapshotProvider = mock(PermissionSnapshotProvider.class);
        props = new PermissionInterceptorProperties();
        interceptor = new PermissionInterceptor(endpointIndex, snapshotProvider, props);
    }

    @AfterEach
    void tearDown() {
        // Nothing — ContextHolder uses ScopedValue; test invocations use runWith.
    }

    private static MockHttpServletRequest req(String method, String path) {
        MockHttpServletRequest r = new MockHttpServletRequest(method, path);
        r.setServletPath(path);
        return r;
    }

    private static <T> T inCtx(Long tenantId, Long userId, java.util.function.Supplier<T> body) {
        Context ctx = new Context();
        ctx.setTenantId(tenantId);
        ctx.setUserId(userId);
        return ContextHolder.callWith(ctx, body::get);
    }

    // ─── public URI bypass ───

    @Test
    void publicUri_bypassesAuth() {
        props.setPublicUriPatterns(List.of("/auth/**"));
        MockHttpServletRequest r = req("POST", "/auth/login");
        assertThat(interceptor.preHandle(r, new MockHttpServletResponse(), null)).isTrue();
    }

    // ─── auth required (no ctx) ───

    @Test
    void authRequired_whenNoContextOrUserId() {
        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        assertThatThrownBy(() -> interceptor.preHandle(r, new MockHttpServletResponse(), null))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("Authentication required");
    }

    @Test
    void authRequired_whenContextHasNoUserId() {
        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        Context ctx = new Context();  // no userId
        ContextHolder.runWith(ctx, () ->
                assertThatThrownBy(() ->
                        interceptor.preHandle(r, new MockHttpServletResponse(), null))
                        .isInstanceOf(PermissionException.class));
    }

    @Test
    void tenantIdMissing_throwsConfigurationException() {
        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        Context ctx = new Context();
        ctx.setUserId(42L);   // no tenantId
        ContextHolder.runWith(ctx, () ->
                assertThatThrownBy(() ->
                        interceptor.preHandle(r, new MockHttpServletResponse(), null))
                        .isInstanceOf(ConfigurationException.class)
                        .hasMessageContaining("missing tenant"));
    }

    // ─── authenticated-bypass patterns ───

    @Test
    void authenticatedBypass_skipsPermissionLookup() {
        props.setAuthenticatedBypassPatterns(List.of("/me/**"));
        MockHttpServletRequest r = req("GET", "/me/uiContext");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
        // Never called EndpointIndex/provider for this bypass path.
        org.mockito.Mockito.verify(endpointIndex, org.mockito.Mockito.never())
                .lookup(anyString(), anyString());
    }

    // ─── super-admin short-circuit ───

    @Test
    void superAdmin_bypassesEndpointCheck() {
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of(PermissionInfo.CODE_SUPER_ADMIN))
                .build();
        when(snapshotProvider.get(eq(10L), eq(42L))).thenReturn(pi);

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
        org.mockito.Mockito.verify(endpointIndex, org.mockito.Mockito.never())
                .lookup(anyString(), anyString());
    }

    @Test
    void superAdmin_doesNotBecomeCrossTenant() {
        // The bypass is about the permission gate, not about tenant isolation. This used to also set
        // crossTenant, which silently widened every read on the request to all tenants and stopped
        // tenant_id being stamped on every write. Reaching across tenants is now opted into per
        // operation (@CrossTenant / ContextUtils windows), so a super-admin request stays in its tenant.
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of(PermissionInfo.CODE_SUPER_ADMIN))
                .build();
        when(snapshotProvider.get(eq(10L), eq(42L))).thenReturn(pi);

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        Context ctx = new Context();
        ctx.setTenantId(10L);
        ctx.setUserId(42L);
        ContextHolder.runWith(ctx, () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));

        assertThat(ctx.isCrossTenant()).isFalse();
    }

    @Test
    void superAdmin_stillGetsEverySystemRoleCode() {
        // The role-code bridge is what @RequireRole reads, and it must survive independently of the
        // tenant flag — the two used to be entangled in one branch, so removing the flag could have
        // taken the expansion with it. A super-admin keeps its own code and gains every framework
        // SystemRole, so a system-role gate is never stricter for it than the permission gate.
        PermissionInfo pi = PermissionInfo.builder()
                .roleCodes(Set.of(PermissionInfo.CODE_SUPER_ADMIN))
                .build();
        when(snapshotProvider.get(eq(10L), eq(42L))).thenReturn(pi);

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        Context ctx = new Context();
        ctx.setTenantId(10L);
        ctx.setUserId(42L);
        ContextHolder.runWith(ctx, () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));

        assertThat(ctx.getRoleCodes()).contains(PermissionInfo.CODE_SUPER_ADMIN);
        assertThat(ctx.getRoleCodes())
                .containsAll(Arrays.stream(SystemRole.values()).map(SystemRole::getCode).toList());
    }

    // ─── unmapped endpoint → 403 ───

    @Test
    void unmappedEndpoint_throwsPermissionException() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(
                PermissionInfo.builder().roleCodes(Set.of("HR")).build());
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());

        MockHttpServletRequest r = req("POST", "/Employee/unknownAction");
        inCtx(10L, 42L, () -> {
            assertThatThrownBy(() ->
                    interceptor.preHandle(r, new MockHttpServletResponse(), null))
                    .isInstanceOf(PermissionException.class)
                    .hasMessageContaining("not registered");
            return null;
        });
    }

    // ─── endpoint mapped but user lacks the permission → 403 ───

    @Test
    void missingPermission_throwsPermissionException() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(
                PermissionInfo.builder()
                        .roleCodes(Set.of("HR"))
                        .permissions(Set.of("other.view"))
                        .build());
        when(endpointIndex.lookup(eq("/Employee/searchList"), eq("POST")))
                .thenReturn(Set.of("employee.view"));

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        inCtx(10L, 42L, () -> {
            assertThatThrownBy(() ->
                    interceptor.preHandle(r, new MockHttpServletResponse(), null))
                    .isInstanceOf(PermissionException.class)
                    .hasMessageContaining("Missing permission");
            return null;
        });
    }

    // ─── user has intersecting permission → allow ───

    @Test
    void userHasPermission_allowed() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(
                PermissionInfo.builder()
                        .roleCodes(Set.of("HR"))
                        .permissions(Set.of("employee.view", "other.view"))
                        .build());
        when(endpointIndex.lookup(eq("/Employee/searchList"), eq("POST")))
                .thenReturn(Set.of("employee.view"));

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
    }

    // ─── shared endpoint reachable via any of multiple permissions ───

    @Test
    void sharedEndpoint_anyPermissionSuffices() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(
                PermissionInfo.builder()
                        .roleCodes(Set.of("HR"))
                        .permissions(Set.of("employee.view"))
                        .build());
        when(endpointIndex.lookup(eq("/Department/searchList"), eq("POST")))
                .thenReturn(Set.of("employee.view", "department.view"));

        MockHttpServletRequest r = req("POST", "/Department/searchList");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
    }

    @Test
    void nullUserPermissions_treatedAsMissing() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(
                PermissionInfo.builder().roleCodes(Set.of("HR")).build());   // permissions=null
        when(endpointIndex.lookup(eq("/Employee/searchList"), eq("POST")))
                .thenReturn(Set.of("employee.view"));

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        inCtx(10L, 42L, () -> {
            assertThatThrownBy(() ->
                    interceptor.preHandle(r, new MockHttpServletResponse(), null))
                    .isInstanceOf(PermissionException.class);
            return null;
        });
    }

    // ─── tenant admin: bypasses the gate, EXCEPT for a module its plan dropped ───
    //
    // A tenant admin holds no static nav grants, so a downgrade has no rows to strip for it and the
    // downgrade cleanup skips it entirely. Its snapshot — already narrowed to the plan by
    // tenantAdminSnapshot — is the only record of what its plan allows, and this branch is the only
    // place that reads it. Without these cases the branch could regress to a blanket `return true`
    // and every test above would still pass.

    /** A tenant admin whose plan dropped payroll: the snapshot carries the surviving permissions only. */
    private static PermissionInfo tenantAdminOnFreePlan() {
        return PermissionInfo.builder()
                .roleCodes(Set.of(PermissionInfo.CODE_TENANT_ADMIN))
                .permissions(Set.of("employee.view", "department.view"))   // no payroll.*
                .build();
    }

    @Test
    void tenantAdmin_deniedOnAModuleThePlanDropped() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(tenantAdminOnFreePlan());
        when(endpointIndex.lookup(eq("/PayItem/searchList"), eq("POST")))
                .thenReturn(Set.of("payroll.pay-item.view"));

        MockHttpServletRequest r = req("POST", "/PayItem/searchList");
        inCtx(10L, 42L, () -> {
            assertThatThrownBy(() ->
                    interceptor.preHandle(r, new MockHttpServletResponse(), null))
                    .isInstanceOf(PermissionException.class)
                    .hasMessageContaining("Missing permission");
            return null;
        });
    }

    @Test
    void tenantAdmin_allowedOnAnEntitledModule() {
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(tenantAdminOnFreePlan());
        when(endpointIndex.lookup(eq("/Employee/searchList"), eq("POST")))
                .thenReturn(Set.of("employee.view"));

        MockHttpServletRequest r = req("POST", "/Employee/searchList");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
    }

    @Test
    void tenantAdmin_stillBypassesAnUnregisteredEndpoint() {
        // The bypass's original job, and the reason the new check is conditional rather than the same
        // code path as a normal user's. Plenty of endpoints carry no permission mapping; a tenant admin
        // is expected to reach them. Denying here would turn a billing gate into a broad outage —
        // note the normal-user path throws "Endpoint not registered" on exactly this input.
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(tenantAdminOnFreePlan());
        when(endpointIndex.lookup(anyString(), anyString())).thenReturn(Set.of());

        MockHttpServletRequest r = req("POST", "/SomeUnmappedThing/doIt");
        boolean allowed = inCtx(10L, 42L,
                () -> interceptor.preHandle(r, new MockHttpServletResponse(), null));
        assertThat(allowed).isTrue();
    }

    @Test
    void tenantAdmin_platformOnlyStillDeniedBeforeTheModuleCheck() {
        // Ordering matters: a platform-only endpoint must report itself as platform-only, not as a
        // missing permission — the two send ops to different places.
        props.setPlatformOnlyPatterns(List.of("/TenantInfo/**"));
        when(snapshotProvider.get(anyLong(), anyLong())).thenReturn(tenantAdminOnFreePlan());

        MockHttpServletRequest r = req("POST", "/TenantInfo/createOne");
        inCtx(10L, 42L, () -> {
            assertThatThrownBy(() ->
                    interceptor.preHandle(r, new MockHttpServletResponse(), null))
                    .isInstanceOf(PermissionException.class)
                    .hasMessageContaining("Platform-admin only");
            return null;
        });
    }
}
