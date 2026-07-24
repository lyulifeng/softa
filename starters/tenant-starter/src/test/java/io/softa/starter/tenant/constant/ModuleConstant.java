package io.softa.starter.tenant.constant;

/**
 * Test-only sample module ids for building {@code plan_entitlement} fixtures in the entitlement
 * tests. These are HCM business module names (the first segment of a navigation id,
 * {@code navigation.<module>.…}) and deliberately do NOT live in {@code src/main} — tenant-starter is
 * a generic multi-tenancy starter and must not ship app-specific module ids in its published jar.
 * The real module ids are owned by the app (its nav tree + {@code plan_entitlement} seed data); the
 * resolver reads them from data, never from constants. Referenced only by {@code EntitlementResolverTest}.
 */
public final class ModuleConstant {

    private ModuleConstant() {}

    public static final String CORE_HR = "core-hr";
    public static final String USERS = "users";
    public static final String SYSTEM = "system";
    public static final String ATTENDANCE = "attendance";
    public static final String ADMIN = "admin";
    public static final String AI = "ai";
    /** Platform-level tool — never sold as a plan module. */
    public static final String STUDIO = "studio";
}
