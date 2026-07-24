package io.softa.starter.tenant.entitlement;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.domain.FilterUnit;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.constant.ModuleConstant;
import io.softa.starter.tenant.constant.PlanConstant;
import io.softa.starter.tenant.entity.Plan;
import io.softa.starter.tenant.entity.PlanEntitlement;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.TenantLifecycle;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Resolution logic for {@link EntitlementResolver}. Feeds a mocked {@link ModelService} (cache always
 * misses) and asserts the effective module set: plan = the 1:1 subscription's planId (degraded to the
 * FALLBACK plan when no sub / lifecycle EXPIRED), modules = plan_entitlement, fail-closed to the
 * fallback set. The fallback is the lowest-{@code tier} plan — <b>no plan id is hardcoded</b>; see
 * {@link #fallbackIsLowestTierPlan_notByHardcodedId()} and {@link #noPlansConfigured_emptyEntitlement()}.
 */
class EntitlementResolverTest {

    private static final long TENANT = 1001L;
    private static final long SUB_ID = 9001L;

    private ModelService<?> modelService;
    private EntitlementResolver resolver;
    private List<TenantSubscription> subs;
    private List<Plan> plans;
    private Map<String, List<String>> planModules;

    @BeforeEach
    void setUp() {
        modelService = mock(ModelService.class);
        CacheService cacheService = mock(CacheService.class);
        when(cacheService.get(anyString(), eq(EntitlementInfo.class))).thenReturn(null);  // always miss → compute
        resolver = new EntitlementResolver(modelService, cacheService);

        subs = new ArrayList<>();
        // Default catalog: free(0) < pro(10) < enterprise(20). Lowest tier (free) is the fallback.
        plans = new ArrayList<>(List.of(
                plan(PlanConstant.PLAN_FREE, 0),
                plan(PlanConstant.PLAN_PRO, 10),
                plan(PlanConstant.PLAN_ENTERPRISE, 20)));
        planModules = new HashMap<>(Map.of(
                PlanConstant.PLAN_FREE, List.of(ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM),
                PlanConstant.PLAN_PRO, List.of(ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM,
                        ModuleConstant.ATTENDANCE, ModuleConstant.ADMIN),
                PlanConstant.PLAN_ENTERPRISE, List.of(ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM,
                        ModuleConstant.ATTENDANCE, ModuleConstant.ADMIN, ModuleConstant.AI)));

        when(modelService.searchList(eq("TenantInfo"), any(FlexQuery.class), eq(TenantInfo.class)))
                .thenAnswer(inv -> List.of(tenantInfo()));
        when(modelService.searchList(eq("TenantSubscription"), any(FlexQuery.class), eq(TenantSubscription.class)))
                .thenAnswer(inv -> subs);
        when(modelService.searchList(eq("PlanEntitlement"), any(FlexQuery.class), eq(PlanEntitlement.class)))
                .thenAnswer(inv -> planEntitlements(filterValue(inv.getArgument(1))));
        when(modelService.searchList(eq("Plan"), any(FlexQuery.class), eq(Plan.class)))
                .thenAnswer(inv -> plansMatching(inv.getArgument(1)));
    }

    // ─── plan / lifecycle ───

    @Test
    void noSubscription_defaultsToFallback() {
        assertThat(modules(TENANT)).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM);
    }

    @Test
    void proPlan_yieldsProModules() {
        subs.add(sub(PlanConstant.PLAN_PRO, TenantLifecycle.SUBSCRIBED));
        assertThat(modules(TENANT)).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM,
                ModuleConstant.ATTENDANCE, ModuleConstant.ADMIN);
    }

    @Test
    void trialEnterprise_yieldsEnterpriseModules() {
        subs.add(sub(PlanConstant.PLAN_ENTERPRISE, TenantLifecycle.TRIAL));  // trial is active
        assertThat(modules(TENANT)).contains(ModuleConstant.AI);
    }

    @Test
    void expiredLifecycle_degradesToFallback() {
        subs.add(sub(PlanConstant.PLAN_ENTERPRISE, TenantLifecycle.EXPIRED));
        assertThat(modules(TENANT)).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM);
        assertThat(resolver.resolve(TENANT).planId()).isEqualTo(PlanConstant.PLAN_FREE);  // = the lowest tier
    }

    @Test
    void scheduledLifecycle_notYetActive_degradesToFallback() {
        // A SCHEDULED sub (start date not yet reached) is NOT active → resolver serves the fallback,
        // not the plan, until the lifecycle job flips it to SUBSCRIBED.
        subs.add(sub(PlanConstant.PLAN_ENTERPRISE, TenantLifecycle.SCHEDULED));
        assertThat(modules(TENANT)).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM);
        assertThat(resolver.resolve(TENANT).planId()).isEqualTo(PlanConstant.PLAN_FREE);
    }

    // ─── fail-closed ───

    @Test
    void unconfiguredPlan_failsClosedToFallbackBase() {
        subs.add(sub(PlanConstant.PLAN_PRO, TenantLifecycle.SUBSCRIBED));
        planModules.remove(PlanConstant.PLAN_PRO);   // pro's modules missing
        assertThat(modules(TENANT)).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM);
    }

    @Test
    void nullTenant_defaultsToFallback() {
        assertThat(resolver.resolve(null).entitledModuleIds()).containsExactlyInAnyOrder(
                ModuleConstant.CORE_HR, ModuleConstant.USERS, ModuleConstant.SYSTEM);
    }

    // ─── fallback is tier-driven, never a hardcoded id ───

    @Test
    void fallbackIsLowestTierPlan_notByHardcodedId() {
        // A deployment whose plans are named nothing like "plan.free" — the lowest tier is the floor.
        plans = new ArrayList<>(List.of(plan("basic", 5), plan("premium", 50)));
        planModules = new HashMap<>(Map.of("basic", List.of(ModuleConstant.CORE_HR)));

        assertThat(resolver.resolve(TENANT).planId()).isEqualTo("basic");           // min tier
        assertThat(resolver.resolve(TENANT).entitledModuleIds()).containsExactly(ModuleConstant.CORE_HR);
    }

    @Test
    void noPlansConfigured_emptyEntitlement() {
        plans = new ArrayList<>();   // empty catalog → no floor → no access

        assertThat(resolver.resolve(TENANT).entitledModuleIds()).isEmpty();
        assertThat(resolver.resolve(TENANT).planId()).isNull();
    }

    // ─── helpers ───

    private Set<String> modules(Long tenantId) {
        return resolver.resolve(tenantId).entitledModuleIds();
    }

    /** Plan mock: an id-filter query = planById (return the match); an empty query = the fallback
     *  lookup (return the whole catalog so the resolver picks min tier). */
    private List<Plan> plansMatching(FlexQuery q) {
        FilterUnit unit = q.getFilters() == null ? null : q.getFilters().getFilterUnit();
        if (unit == null) {
            return plans;
        }
        String id = String.valueOf(unit.getValue());
        return plans.stream().filter(p -> id.equals(p.getId())).toList();
    }

    private List<PlanEntitlement> planEntitlements(String planId) {
        List<PlanEntitlement> out = new ArrayList<>();
        for (String m : planModules.getOrDefault(planId, List.of())) {
            PlanEntitlement e = new PlanEntitlement();
            e.setPlanId(planId);
            e.setModuleId(m);
            out.add(e);
        }
        return out;
    }

    private static String filterValue(FlexQuery q) {
        return String.valueOf(q.getFilters().getFilterUnit().getValue());
    }

    private static TenantSubscription sub(String planId, TenantLifecycle lifecycle) {
        TenantSubscription s = new TenantSubscription();
        s.setId(SUB_ID);
        s.setPlanId(planId);
        s.setLifecycle(lifecycle);
        return s;
    }

    /** The tenant registry row — points at the version via subscriptionId (null when none). */
    private TenantInfo tenantInfo() {
        TenantInfo t = new TenantInfo();
        t.setId(TENANT);
        t.setSubscriptionId(subs.isEmpty() ? null : subs.get(0).getId());
        return t;
    }

    private static Plan plan(String id, int tier) {
        Plan p = new Plan();
        p.setId(id);
        p.setTier(tier);
        return p;
    }
}
