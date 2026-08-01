package io.softa.framework.base.utils;

/**
 * Helpers for {@code navigation.*} ids. The <b>module id</b> is the first path segment after the
 * {@code navigation.} prefix — e.g. {@code navigation.core-hr.employee.list → core-hr}. This mirrors
 * the FE {@code navModuleOf}; kept as the single BE definition so the RBAC consumers cannot drift —
 * {@code UiContextBuilder} plan-narrowing and {@code EntitlementRoleCleanupService} downgrade cleanup
 * in user-starter, and the snapshot / route gate in permission-starter.
 *
 * <p>Lives in softa-base rather than in either starter because those two do not depend on each other
 * (by design — permission-starter is "zero user-starter"), so a copy in each is the only alternative,
 * and a second copy is exactly what this class exists to prevent. Base rather than orm because the
 * function is a string split with no ORM concept in it — it belongs beside {@code StringTools}, not
 * beside {@code IdUtils}. It reads the module id that {@code EntitlementService.entitledModules}
 * returns, so the two must agree on what a module id is.
 */
public final class NavIds {

    private static final String NAV_PREFIX = "navigation.";

    private NavIds() {
    }

    /** Module id = first segment after {@code navigation.} (tolerates a prefix-less id; null/blank → null). */
    public static String moduleOf(String navId) {
        if (navId == null || navId.isBlank()) {
            return null;
        }
        String s = navId.startsWith(NAV_PREFIX) ? navId.substring(NAV_PREFIX.length()) : navId;
        int dot = s.indexOf('.');
        return dot >= 0 ? s.substring(0, dot) : s;
    }
}
