package io.softa.starter.permission.scope;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.EmpInfo;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The enricher's own DB reads must bypass row scope.
 *
 * <p>Otherwise they deadlock: row scope resolves SELF / DEPT_SUBTREE against
 * {@code Context.empInfo}, which is what these reads exist to produce — so a non-admin
 * caller matched no Employee row, EmpInfo stayed null for the whole request, every
 * employee-anchored scope silently degraded to "no rows", and a CUSTOM rule referencing
 * {@code USER_EMP_ID} failed at SQL-build time.
 *
 * <p>The bypass used to be declared with {@code @SkipPermissionCheck}, which is Spring-AOP
 * advice and never fired here (self-invocation + a private method), so these tests assert the
 * flag's actual state around the read rather than the annotation's presence.
 */
class EmployeeContextEnricherTest {

    private ModelService<Long> modelService;
    private CacheService cacheService;
    private EmployeeContextEnricher enricher;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        cacheService = mock(CacheService.class);
        enricher = new EmployeeContextEnricher(modelService, cacheService);
    }

    @Test
    void dbRead_runsWithScopeBypass_andRestoresTheFlag() {
        AtomicBoolean bypassedDuringRead = new AtomicBoolean(false);
        when(modelService.searchOne(eq("Employee"), any(FlexQuery.class))).thenAnswer(inv -> {
            bypassedDuringRead.set(ContextHolder.getContext().isSkipPermissionCheck());
            return Optional.of(Map.of("id", 7L, "departmentId", 3L));
        });
        when(modelService.searchList(eq("Department"), any(FlexQuery.class))).thenReturn(List.of());

        Context ctx = ctx(1L);
        enrichWithModels(ctx);

        assertThat(bypassedDuringRead).isTrue();          // the read did not go through row scope
        assertThat(ctx.isSkipPermissionCheck()).isFalse();  // and the flag was handed back
        assertThat(ctx.getEmpInfo()).isNotNull();
        assertThat(ctx.getEmpInfo().getEmpId()).isEqualTo(7L);
        assertThat(ctx.getEmpInfo().getDeptId()).isEqualTo(3L);
    }

    @Test
    void flagIsRestored_evenWhenTheReadThrows() {
        when(modelService.searchOne(eq("Employee"), any(FlexQuery.class)))
                .thenThrow(new IllegalStateException("boom"));

        Context ctx = ctx(1L);
        assertThatThrownBy(() -> enrichWithModels(ctx)).isInstanceOf(IllegalStateException.class);

        // A leaked flag would silently disable row scope for the rest of the request.
        assertThat(ctx.isSkipPermissionCheck()).isFalse();
    }

    @Test
    void userWithNoEmployeeRow_staysAPureUser() {
        when(modelService.searchOne(eq("Employee"), any(FlexQuery.class))).thenReturn(Optional.empty());

        Context ctx = ctx(1L);
        enrichWithModels(ctx);

        assertThat(ctx.getEmpInfo()).isNull();
        assertThat(ctx.isSkipPermissionCheck()).isFalse();
    }

    @Test
    void anonymousRequest_isSkippedEntirely() {
        enrichWithModels(ctx(null));
        Mockito.verifyNoInteractions(modelService, cacheService);
    }

    // ─────────────────────── helpers ───────────────────────

    private void enrichWithModels(Context ctx) {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.existModel("Employee")).thenReturn(true);
            mm.when(() -> ModelManager.existModel("Department")).thenReturn(true);
            ContextHolder.runWith(ctx, () -> enricher.enrich(ctx));
        }
    }

    private static Context ctx(Long userId) {
        Context ctx = new Context();
        ctx.setUserId(userId);
        return ctx;
    }
}
