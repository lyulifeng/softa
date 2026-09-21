package io.softa.starter.user.controller;

import java.io.Serializable;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.service.SystemRoleWriteGuard;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The guarded endpoints, reached the way a request reaches them: through the proxy.
 *
 * <p>Every one of these controllers is a {@code @RestController}, and {@code ApiExceptionAspect}
 * advises {@code @within(@RestController)} — so Spring wraps each of them in a CGLIB proxy whether or
 * not it carries {@code @Transactional}. CGLIB proxies by subclassing, which means a {@code final}
 * method cannot be overridden: the call lands on the proxy instance and runs the body there. Spring
 * injects the target, never the proxy, so every {@code @Autowired} field the body reads is null.
 *
 * <p>That is what took the role endpoints down: {@code POST /Role/deleteById} answered 500 with
 * {@code Cannot invoke "SystemRoleWriteGuard.guardByIds(...)" because "this.writeGuard" is null}.
 * All fifteen mapped write verbs share the shape, on all four guarded models.
 *
 * <p>The existing controller tests construct with {@code new RoleController(...)} and so exercise the
 * target directly — correct for what they assert, and structurally unable to see this. The proxy is
 * the missing half, so it is built here explicitly.
 */
class GuardedControllerUnderProxyTest {

    /** A minimal guarded controller: the base class is what is under test, not any one model. */
    static class ProbeController
            extends SystemRoleGuardedController<io.softa.framework.orm.service.EntityService<
                    io.softa.framework.orm.entity.AbstractModel, Serializable>,
                    io.softa.framework.orm.entity.AbstractModel, Serializable> {
        @Override
        protected String modelName() {
            return "Role";
        }
    }

    private static final String MODEL = "Role";

    /** A target wired the way Spring wires it: fields set on the instance the container manages. */
    private static ProbeController target(SystemRoleWriteGuard guard, ModelService<Serializable> models) {
        ProbeController controller = new ProbeController();
        ReflectionTestUtils.setField(controller, "writeGuard", guard);
        ReflectionTestUtils.setField(controller, "modelService", models);
        return controller;
    }

    /** The CGLIB proxy Spring puts in front of it — same posture, no advice needed to reproduce. */
    private static ProbeController proxied(ProbeController target) {
        ProxyFactory factory = new ProxyFactory(target);
        factory.setProxyTargetClass(true);
        return (ProbeController) factory.getProxy();
    }

    @Test
    @DisplayName("a delete through the proxy reaches the guard rather than throwing NPE")
    void deleteByIdSurvivesTheProxy() {
        SystemRoleWriteGuard guard = mock(SystemRoleWriteGuard.class);
        @SuppressWarnings("unchecked")
        ModelService<Serializable> models = mock(ModelService.class);
        when(models.deleteById(anyString(), any())).thenReturn(true);

        ProbeController endpoint = proxied(target(guard, models));

        assertThatCode(() -> endpoint.deleteById("r1")).doesNotThrowAnyException();
        // The guard must still run — a fix that merely stops the NPE while skipping the check would
        // leave built-in roles writable, which is the hole the guard exists to close.
        verify(guard).guardByIds(MODEL, List.of("r1"));
    }

    @Test
    @DisplayName("every mapped write verb survives the proxy, not just the reported one")
    void everyGuardedVerbSurvivesTheProxy() {
        SystemRoleWriteGuard guard = mock(SystemRoleWriteGuard.class);
        @SuppressWarnings("unchecked")
        ModelService<Serializable> models = mock(ModelService.class);
        ProbeController endpoint = proxied(target(guard, models));

        Map<String, Object> row = Map.of("id", "r1");
        List<Map<String, Object>> rows = List.of(row);

        // Each of these reads an injected field in a final method body — the failing shape.
        assertThatCode(() -> {
            endpoint.createOne(row);
            endpoint.createList(rows);
            endpoint.updateOne(row);
            endpoint.updateList(rows);
            endpoint.deleteById("r1");
            endpoint.deleteByIds(List.of("r1"));
            endpoint.copyById("r1");
            endpoint.copyByIds(List.of("r1"));
        }).doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the proxy really is the CGLIB kind, so the test is not passing by accident")
    void theProbeIsActuallyProxied() {
        ProbeController endpoint = proxied(target(mock(SystemRoleWriteGuard.class), null));

        // Without this the two tests above would also pass on a plain instance, proving nothing.
        assertThat(endpoint.getClass()).isNotEqualTo(ProbeController.class);
        assertThat(ProbeController.class.isAssignableFrom(endpoint.getClass())).isTrue();
        assertThat(endpoint.getClass().getName()).contains("$$");
    }

    @Test
    @DisplayName("no subclass overrides the accessors, which would re-open what final closes")
    void subclassesDoNotSubstituteTheGuard() {
        // `guard()` has to stay overridable for CGLIB, which means a subclass could return a no-op
        // and walk past the check — the exact hole the final mapped methods exist to close. Nothing
        // in the language prevents it, so it is asserted instead.
        List<Class<?>> guarded = List.of(
                RoleController.class,
                RoleDataScopeController.class,
                RoleNavigationController.class,
                RoleSensitiveFieldSetController.class);

        for (Class<?> type : guarded) {
            assertThatCode(() -> type.getDeclaredMethod("guard"))
                    .as("%s must not substitute the guard", type.getSimpleName())
                    .isInstanceOf(NoSuchMethodException.class);
            assertThatCode(() -> type.getDeclaredMethod("models"))
                    .as("%s must not substitute the model service", type.getSimpleName())
                    .isInstanceOf(NoSuchMethodException.class);
        }
    }
}
