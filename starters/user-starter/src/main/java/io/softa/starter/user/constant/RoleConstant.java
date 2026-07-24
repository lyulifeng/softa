package io.softa.starter.user.constant;

import io.softa.starter.user.entity.Role;

/**
 * Reserved {@code Role.code} values + related role-scoped constants and
 * pure-function helpers.
 *
 * <p>System-reserved roles have non-null {@code code} (machine identifier);
 * admin-created roles always have null code. Adding a new reserved code:
 * append a static here AND register it in
 * {@code PermissionRegistryValidator}'s reserved-code set so admins can't
 * squat on it.
 */
public final class RoleConstant {

    /**
     * Reserved code for the system Super-Admin role.
     *
     * <p>Holders short-circuit every permission check — the endpoint gate
     * ({@code PermissionInterceptor}), the row-scope filter
     * ({@code ScopeFilterAspect}), the response field mask
     * ({@code FieldFilter}) and the field write guard
     * ({@code FieldWriteGuardAspect}) all consult
     * {@code PermissionInfo.isSuperAdmin(pi)} which tests this code.
     *
     * <p>The role row itself cannot be deleted / renamed / made inactive;
     * the service layer also rejects revoking the last Manual holder.
     */
    public static final String CODE_SUPER_ADMIN = "SUPER_ADMIN";

    /**
     * Reserved code for a tenant super-admin role. Holders bypass the permission gate WITHIN
     * their own tenant (tenant-isolated — NOT cross-tenant), but are denied platform-only Ops
     * endpoints (billing / provisioning; see {@code PermissionInterceptorProperties.platformOnlyPatterns}).
     * Seeded per-tenant by provisioning; the first tenant admin is granted this role.
     */
    public static final String CODE_TENANT_ADMIN = "TENANT_ADMIN";

    /** Null-safe — true when the given role is the reserved tenant super-admin role. */
    public static boolean isTenantAdmin(Role role) {
        return role != null && CODE_TENANT_ADMIN.equals(role.getCode());
    }

    /**
     * True when the given role is the system-reserved Super-Admin role.
     *
     * <p>Lives here (not on {@link Role}) so the entity stays a pure data
     * carrier — business predicates against {@code code} belong with the
     * reserved-code definition, not the JPA row class.
     *
     * <p>Null-safe: {@code role == null} → false.
     */
    public static boolean isSuperAdmin(Role role) {
        return role != null && CODE_SUPER_ADMIN.equals(role.getCode());
    }

    /**
     * True when the given role is any system-reserved role — it has a non-null {@code code}
     * (SUPER_ADMIN / TENANT_ADMIN / any future built-in). Admin-created roles have {@code code = null}.
     * Null-safe. Used to protect built-in roles from admin edit / delete.
     */
    public static boolean isSystemRole(Role role) {
        return role != null && role.getCode() != null && !role.getCode().isBlank();
    }

    private RoleConstant() {
        // utility class — no instances
    }
}
