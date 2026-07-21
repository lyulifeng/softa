package io.softa.starter.user.dto;

import java.util.Set;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Data;

/**
 * Read-only computed effective access for an admin ROLE ({@code GET /Role/{id}/effective-access},
 * role-detail display). {@code computed = true} for a bypass admin role (SUPER_ADMIN / TENANT_ADMIN),
 * carrying the runtime-computed navs/permissions + the admin-bypass flags; {@code computed = false} for
 * a non-admin role — the FE then renders that role's static {@code role_navigation} grants instead.
 *
 * <p>{@code @JsonInclude(NON_NULL)} omits the admin-only fields for a non-admin role, so its response is
 * just {@code {"computed": false}} (mirroring the FE's optional fields).
 */
@Data
@JsonInclude(JsonInclude.Include.NON_NULL)
public class EffectiveAccess {

    /** Whether this is a runtime-computed (bypass admin) access; {@code false} for a non-admin role. */
    private boolean computed;

    /** Admin role code (SUPER_ADMIN / TENANT_ADMIN); null for a non-admin role. */
    private String roleCode;

    /** Navigation ids the admin role effectively sees. */
    private Set<String> navigations;

    /** Permission ids the admin role effectively has. */
    private Set<String> permissions;

    /** {@code true} — an admin bypasses row-scope entirely (sees all rows). */
    private Boolean dataScopeAll;

    /** {@code true} — an admin sees all sensitive fields. */
    private Boolean sensitiveAll;
}
