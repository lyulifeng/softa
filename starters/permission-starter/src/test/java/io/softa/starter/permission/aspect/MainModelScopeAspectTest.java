package io.softa.starter.permission.aspect;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.reflect.MethodSignature;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.web.bind.annotation.RequestMapping;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.PermissionService;
import io.softa.starter.permission.annotation.MainModelScope;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The aspect's contract, in order of importance: ① the main-model check runs
 * BEFORE the bypass opens (with the caller's real scope), ② the bypass is the
 * narrow {@code skipDataScope} flag, opened only on success and restored on
 * exit, ③ {@code filterParam} arguments are rewritten, not checked, ④
 * resolution failures carry the concrete fix.
 */
class MainModelScopeAspectTest {

    // ── fixture controllers ──────────────────────────────────────────────

    @RequestMapping("/LeaveRequest")
    static class LeaveRequestController {

        @MainModelScope(idParam = "leaveRequestId")
        public void approve(Long leaveRequestId) {
        }

        @MainModelScope(filterParam = "filter")
        public List<Map<String, Object>> customList(Filters filter) {
            return List.of();
        }

        @MainModelScope(model = "Employee", idParam = "empIds")
        @MainModelScope(idParam = "reqId")
        public void transfer(List<Long> empIds, Long reqId) {
        }

        @MainModelScope(idParam = "noSuchParam")
        public void typoParam(Long id) {
        }

        @MainModelScope(filterParam = "condition")
        public void dtoFilter(Map<String, Object> condition) {
        }

        @MainModelScope
        public void nothingDeclared(Long id) {
        }

        @MainModelScope(idParam = "ids")
        public void primitiveIds(long[] ids) {
        }

        public void notAnnotated(Long id) {
        }
    }

    static class AggregateController {

        @MainModelScope(idParam = "id")
        public void noRoute(Long id) {
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private static Method method(Class<?> owner, String name, Class<?>... params) {
        try {
            return owner.getMethod(name, params);
        } catch (NoSuchMethodException e) {
            throw new AssertionError(e);
        }
    }

    /** A join point over a real reflective Method, with controllable args/body. */
    private static ProceedingJoinPoint joinPoint(Method m, Object[] args, ThrowingFn body)
            throws Throwable {
        MethodSignature sig = mock(MethodSignature.class);
        when(sig.getMethod()).thenReturn(m);
        ProceedingJoinPoint jp = mock(ProceedingJoinPoint.class);
        when(jp.getSignature()).thenReturn(sig);
        when(jp.getArgs()).thenReturn(args);
        when(jp.proceed(any(Object[].class))).thenAnswer(inv -> body.apply(inv.getArgument(0)));
        return jp;
    }

    interface ThrowingFn {
        Object apply(Object[] args) throws Throwable;
    }

    private static Context userContext() {
        Context ctx = new Context();
        ctx.setUserId(7L);
        return ctx;
    }

    // ── ① check-then-bypass ordering ─────────────────────────────────────

    @Test
    void idsChecked_thenNarrowFlagOpens_andRestores() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "approve", Long.class);

        AtomicBoolean flagDuringBody = new AtomicBoolean(false);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{42L}, args -> {
            flagDuringBody.set(ContextHolder.getContext().isSkipDataScope());
            return null;
        });

        Context ctx = userContext();
        ContextHolder.callWith(ctx, () -> aspect.enforce(jp));

        // model inferred from /LeaveRequest, single id boxed; READ is fixed —
        // row scope has no direction, the annotation exposes no accessType
        @SuppressWarnings("unchecked")
        ArgumentCaptor<Collection<Serializable>> ids = ArgumentCaptor.forClass(Collection.class);
        verify(permissionService).checkIdsAccess(eq("LeaveRequest"), ids.capture(), eq(AccessType.READ));
        assertThat(ids.getValue()).containsExactly(42L);
        // narrow flag was open inside the body, closed again after
        assertThat(flagDuringBody).isTrue();
        assertThat(ctx.isSkipDataScope()).isFalse();
    }

    @Test
    void outOfScopeId_rejects_beforeBodyRuns_andFlagStaysClosed() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        doThrow(new io.softa.framework.base.exception.PermissionException("out of scope"))
                .when(permissionService).checkIdsAccess(anyString(), anyCollection(), any());
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "approve", Long.class);

        AtomicBoolean bodyRan = new AtomicBoolean(false);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{42L}, args -> {
            bodyRan.set(true);
            return null;
        });

        Context ctx = userContext();
        assertThatThrownBy(() -> ContextHolder.callWith(ctx, () -> aspect.enforce(jp)))
                .hasMessageContaining("out of scope");
        assertThat(bodyRan).isFalse();
        assertThat(ctx.isSkipDataScope()).isFalse();
    }

    @Test
    void flagRestored_evenWhenBodyThrows() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "approve", Long.class);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{42L}, args -> {
            throw new IllegalStateException("business failure");
        });

        Context ctx = userContext();
        assertThatThrownBy(() -> ContextHolder.callWith(ctx, () -> aspect.enforce(jp)))
                .hasMessageContaining("business failure");
        assertThat(ctx.isSkipDataScope()).isFalse();
    }

    // ── ③ filter rewrite ─────────────────────────────────────────────────

    @Test
    void filterParam_isRewritten_notChecked() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        Filters scoped = Filters.of("employeeId", Operator.EQUAL, 7L);
        when(permissionService.appendScopeAccessFilters(eq("LeaveRequest"), any()))
                .thenReturn(scoped);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "customList", Filters.class);

        Object[] seenByBody = new Object[1];
        Filters original = new Filters();
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{original}, args -> {
            seenByBody[0] = args[0];
            return List.of();
        });

        ContextHolder.callWith(userContext(), () -> aspect.enforce(jp));

        assertThat(seenByBody[0]).isSameAs(scoped);          // the body got the AND-ed filter
        verify(permissionService, never()).checkIdsAccess(anyString(), anyCollection(), any());
    }

    @Test
    void nullFilterArgument_isReplacedWithEmptyFilters_beforeAppend() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        when(permissionService.appendScopeAccessFilters(anyString(), any()))
                .thenAnswer(inv -> inv.getArgument(1));
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "customList", Filters.class);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{null}, args -> List.of());

        ContextHolder.callWith(userContext(), () -> aspect.enforce(jp));

        ArgumentCaptor<Filters> passed = ArgumentCaptor.forClass(Filters.class);
        verify(permissionService).appendScopeAccessFilters(eq("LeaveRequest"), passed.capture());
        assertThat(passed.getValue()).isNotNull();
    }

    // ── repeatable / multi-model ─────────────────────────────────────────

    @Test
    void stackedAnnotations_checkEachModel() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "transfer", List.class, Long.class);
        ProceedingJoinPoint jp = joinPoint(m,
                new Object[]{List.of(1L, 2L), 42L}, args -> null);

        ContextHolder.callWith(userContext(), () -> aspect.enforce(jp));

        verify(permissionService).checkIdsAccess(eq("Employee"), eq(List.of(1L, 2L)), eq(AccessType.READ));
        verify(permissionService).checkIdsAccess(eq("LeaveRequest"), eq(List.of(42L)), eq(AccessType.READ));
    }

    @Test
    void nullIdArgument_failsClosed_bypassNeverOpens() throws Throwable {
        // checkIdsAccess returns silently for an empty collection, so a null id
        // reaching it would open the bypass with NOTHING verified — an endpoint
        // that can also locate data by code/employeeId would be reachable at
        // full range simply by omitting the id. The aspect must reject first.
        PermissionService permissionService = mock(PermissionService.class);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "approve", Long.class);
        AtomicBoolean bodyRan = new AtomicBoolean(false);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{null}, args -> {
            bodyRan.set(true);
            return null;
        });

        Context ctx = userContext();
        assertThatThrownBy(() -> ContextHolder.callWith(ctx, () -> aspect.enforce(jp)))
                .hasMessageContaining("required");
        assertThat(bodyRan).isFalse();
        assertThat(ctx.isSkipDataScope()).isFalse();
        verify(permissionService, never()).checkIdsAccess(anyString(), anyCollection(), any());
    }

    @Test
    void emptyIdCollection_failsClosed_sameAsNull() throws Throwable {
        PermissionService permissionService = mock(PermissionService.class);
        MainModelScopeAspect aspect = new MainModelScopeAspect(permissionService);
        Method m = method(LeaveRequestController.class, "transfer", List.class, Long.class);
        ProceedingJoinPoint jp = joinPoint(m, new Object[]{List.of(), 42L}, args -> null);

        assertThatThrownBy(() -> ContextHolder.callWith(userContext(), () -> aspect.enforce(jp)))
                .hasMessageContaining("required");
    }

    // ── ④ resolution failures name the fix ───────────────────────────────

    @Test
    void typoParamName_failsWithAvailableNames() {
        Method m = method(LeaveRequestController.class, "typoParam", Long.class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("noSuchParam")
                .hasMessageContaining("-parameters");
    }

    @Test
    void nonFiltersFilterParam_failsNamingTheManualFallback() {
        Method m = method(LeaveRequestController.class, "dtoFilter", Map.class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("scope-rewritten")
                .hasMessageContaining("manually");
    }

    @Test
    void neitherIdNorFilter_fails() {
        Method m = method(LeaveRequestController.class, "nothingDeclared", Long.class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("neither idParam nor filterParam");
    }

    @Test
    void primitiveArrayIdParam_failsAtResolve() {
        Method m = method(LeaveRequestController.class, "primitiveIds", long[].class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("primitive array");
    }

    @Test
    void unresolvableAnnotation_failsInsteadOfUncheckedBypass() {
        // If the pointcut matches but the Method the signature hands back carries
        // no resolvable annotation (interface proxy / bridge method), resolving to
        // an empty scope list would mean "no checks, bypass opens". Refuse.
        Method m = method(LeaveRequestController.class, "notAnnotated", Long.class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("refusing to open");
    }

    @Test
    void noRouteAndNoModel_failsAskingForExplicitModel() {
        Method m = method(AggregateController.class, "noRoute", Long.class);
        assertThatThrownBy(() -> MainModelScopeAspect.resolve(m))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("Declare model=");
    }
}
