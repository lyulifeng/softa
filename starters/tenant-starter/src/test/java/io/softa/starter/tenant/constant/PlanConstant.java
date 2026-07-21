package io.softa.starter.tenant.constant;

/**
 * Test-only sample plan ids for the entitlement tests. Real plan ids are deployment-authored seed data —
 * the framework hardcodes none (the fallback plan is the lowest {@code Plan.tier}; see
 * {@code EntitlementResolver}). These sample ids + their tier ordering (Free &lt; Pro &lt; Enterprise)
 * only build readable fixtures. Referenced only by {@code EntitlementResolverTest}.
 */
public final class PlanConstant {

    private PlanConstant() {}

    public static final String PLAN_FREE = "plan.free";
    public static final String PLAN_PRO = "plan.pro";
    public static final String PLAN_ENTERPRISE = "plan.enterprise";
}
