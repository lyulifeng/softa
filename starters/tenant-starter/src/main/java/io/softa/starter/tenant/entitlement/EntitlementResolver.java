package io.softa.starter.tenant.entitlement;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.Plan;
import io.softa.starter.tenant.entity.PlanEntitlement;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

/**
 * Resolves a tenant's effective entitlement (the {@link EntitlementInfo} behind the
 * {@link io.softa.framework.orm.service.EntitlementService} SPI). Self-contained in
 * tenant-starter: reads the tenant's 1:1 {@code TenantSubscription} (via
 * {@code TenantInfo.subscriptionId}) + plan_entitlement, never the nav tree — so tenant-starter
 * needs no user-starter dependency.
 *
 * <h3>Resolution</h3>
 * <pre>
 *   sub     = TenantInfo(tenantId).subscriptionId → tenant_subscription   (owned 1:1)
 *   plan    = sub.planId   (degraded to the FALLBACK plan if no sub / null, or lifecycle = EXPIRED)
 *   modules = ( plan_entitlement.moduleId WHERE planId = plan )
 *   fail-closed: an empty/unconfigured plan set → the fallback plan's set, never a partial one.
 * </pre>
 * Version axis = {@code planId} (which plan) + {@code lifecycle} (TRIAL / SUBSCRIBED / GRACE_PERIOD
 * active; EXPIRED degrades). {@code effectiveFrom}/{@code effectiveTo} are NOT read here — the resolver
 * gates on {@code lifecycle}; {@code SubscriptionExpiryJob} does the time-driven flip → EXPIRED.
 *
 * <h3>Fallback plan (no hardcoded id)</h3>
 * The degrade target is the catalog's <b>lowest-tier plan</b> (min {@code Plan.tier}), NOT a fixed id
 * like {@code "plan.free"} — so any deployment's own plan naming works out of the box. If the catalog
 * has no plan at all, the fallback is the <b>empty</b> entitlement (unpaid ⇒ no access); a deployment
 * that wants a free floor simply seeds a lowest-tier plan with a base module set.
 *
 * <h3>Caching</h3>
 * Redis {@code entl:{tenantId}} (TTL 1h) → DB resolve.
 *
 * <p>All reads run under {@link SkipPermissionCheck} — fires on the cross-bean call from
 * {@code EntitlementServiceImpl}.
 */
@Slf4j
@Component
public class EntitlementResolver {

    private final ModelService<?> modelService;
    private final CacheService cacheService;

    public EntitlementResolver(ModelService<?> modelService, CacheService cacheService) {
        this.modelService = modelService;
        this.cacheService = cacheService;
    }

    /** Resolve (cache-aside) the tenant's effective entitlement. Never null. */
    @SkipPermissionCheck
    public EntitlementInfo resolve(Long tenantId) {
        if (tenantId == null) {
            return fallbackInfo();
        }
        String key = RedisConstant.ENTITLEMENT + tenantId;
        EntitlementInfo cached = cacheService.get(key, EntitlementInfo.class);
        if (cached != null) {
            return cached;
        }
        EntitlementInfo info = compute(tenantId);
        cacheService.save(key, info, RedisConstant.ONE_HOUR);
        return info;
    }

    /** Evict the tenant's {@code entl:} snapshot — call after any plan / lifecycle change. */
    public void evict(Long tenantId) {
        if (tenantId != null) {
            cacheService.clear(RedisConstant.ENTITLEMENT + tenantId);
        }
    }

    private EntitlementInfo compute(Long tenantId) {
        TenantSubscription sub = loadSubscription(tenantId);
        // Active subscription on a real plan → use it; otherwise (no sub / null plan / EXPIRED) degrade
        // to the fallback plan.
        if (sub != null && sub.getPlanId() != null
                && sub.getLifecycle() != null && sub.getLifecycle().isEntitlementActive()) {
            Set<String> modules = new HashSet<>(planModules(sub.getPlanId()));
            if (!modules.isEmpty()) {
                Plan plan = planById(sub.getPlanId());
                int tier = (plan != null && plan.getTier() != null) ? plan.getTier() : 0;
                return new EntitlementInfo(sub.getPlanId(), tier, modules);
            }
            // plan_entitlement missing / plan deleted → fail closed to the fallback plan below,
            // never a partial / 0-module set (which would look like site-wide loss of access).
        }
        return fallbackInfo();
    }

    /** The fallback (floor) entitlement — the lowest-tier plan's set; empty when the catalog is empty. */
    private EntitlementInfo fallbackInfo() {
        Plan fb = fallbackPlan();
        if (fb == null) {
            // No plans configured → no floor → no modules. A deployment wanting a free floor seeds a
            // lowest-tier plan with its base modules.
            return new EntitlementInfo(null, 0, Set.of());
        }
        int tier = fb.getTier() != null ? fb.getTier() : 0;
        return new EntitlementInfo(fb.getId(), tier, new HashSet<>(planModules(fb.getId())));
    }

    /** The catalog's floor plan = the smallest {@code tier} (ties broken by id); null if no plans. */
    private Plan fallbackPlan() {
        List<Plan> plans = modelService.searchList("Plan", new FlexQuery(new Filters()), Plan.class);
        return plans.stream()
                .filter(p -> p.getTier() != null)
                .min(Comparator.comparingInt(Plan::getTier).thenComparing(Plan::getId))
                .orElse(null);
    }

    private TenantSubscription loadSubscription(Long tenantId) {
        // Flipped FK: TenantInfo owns subscriptionId → TenantSubscription (1:1). Read the registry
        // row, then the version it points at. No tenant / no subscriptionId → no version → fallback.
        List<TenantInfo> tenants = modelService.searchList("TenantInfo",
                new FlexQuery(Filters.of("id", Operator.EQUAL, tenantId)), TenantInfo.class);
        if (tenants.isEmpty() || tenants.get(0).getSubscriptionId() == null) {
            return null;
        }
        List<TenantSubscription> subs = modelService.searchList("TenantSubscription",
                new FlexQuery(Filters.of("id", Operator.EQUAL, tenants.get(0).getSubscriptionId())),
                TenantSubscription.class);
        return subs.isEmpty() ? null : subs.get(0);
    }

    private Set<String> planModules(String planId) {
        if (planId == null) {
            return Set.of();
        }
        List<PlanEntitlement> rows = modelService.searchList("PlanEntitlement",
                new FlexQuery(Filters.of("planId", Operator.EQUAL, planId)), PlanEntitlement.class);
        Set<String> out = new HashSet<>();
        for (PlanEntitlement r : rows) {
            if (r.getModuleId() != null) {
                out.add(r.getModuleId());
            }
        }
        return out;
    }

    private Plan planById(String planId) {
        if (planId == null) {
            return null;
        }
        List<Plan> plans = modelService.searchList("Plan",
                new FlexQuery(Filters.of("id", Operator.EQUAL, planId)), Plan.class);
        return plans.isEmpty() ? null : plans.get(0);
    }
}
