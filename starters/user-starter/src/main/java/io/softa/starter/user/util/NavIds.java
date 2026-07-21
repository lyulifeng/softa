package io.softa.starter.user.util;

/**
 * Helpers for {@code navigation.*} ids. The <b>module id</b> is the first path segment after the
 * {@code navigation.} prefix — e.g. {@code navigation.core-hr.employee.list → core-hr}. This mirrors
 * the FE {@code navModuleOf}; kept as the single BE definition so the RBAC consumers
 * ({@code UiContextBuilder} plan-narrowing, {@code EntitlementRoleCleanupService} downgrade cleanup)
 * cannot drift.
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
