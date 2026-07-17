package io.softa.starter.permission.spi;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Set;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Runtime permission snapshot for one user — built once at login by the enrich
 * side ({@code PermissionInfoEnricher}) and cached in Redis
 * (key: {@code perm-v2:{tenantId}:user:{userId}}, TTL 1h).
 *
 * <p>2026-07-14: unified — the former {@code user-starter} rich {@code dto.PermissionInfo}
 * merged into this single framework-base type, so the enforce side
 * ({@code permission-starter} interceptor + data-plane) can read the snapshot via
 * the {@code PermissionSnapshotProvider} SPI without depending on {@code user-starter}.
 * {@code Context} carries this type; both build and enforce share it.
 *
 * <p>Does NOT carry the URL allowed-set — URL → permissionId resolution lives in the
 * {@code EndpointIndex} singleton (permission-starter). The interceptor looks up the
 * endpoint there, then checks {@link #permissions}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "User permission snapshot (runtime cache)")
public class PermissionInfo implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /** Role code that identifies a platform super-admin (cross-tenant, all menus). */
    public static final String CODE_SUPER_ADMIN = "SUPER_ADMIN";

    @Schema(description = "Role codes the user holds (display + super-admin check; auth decisions use permissions / nav sets)")
    private Set<String> roleCodes;

    @Schema(description = "Legacy grouped permission codes (model → codes); kept for framework aspects")
    private Map<String, Set<String>> permissionCodes;

    @Schema(description = "Navigation IDs visible to the user (flat; ancestors auto-expanded)")
    private Set<String> navigations;

    @Schema(description = "Permission IDs granted to the user (flat set; interceptor intersects with endpoint→permission)")
    private Set<String> permissions;

    @Schema(description = "Model → scope rules aggregated across all role_navigation rows of same model. OR-combined at runtime.")
    private Map<String, List<ScopeRule>> modelScopeMap;

    @Schema(description = "Model → granted sensitive_field_set IDs. FieldFilter expands setIds → fieldCodes at response time.")
    private Map<String, Set<String>> modelSensitiveFieldSetsMap;

    /**
     * Single source of truth for the SUPER_ADMIN short-circuit consulted by every
     * layer (route-admission + data-plane + enricher). True iff the user holds the
     * {@link #CODE_SUPER_ADMIN} role.
     *
     * <p>Null-safe: callers can write {@code if (pi.isSuperAdmin()) ...} without
     * {@code pi != null} guards; static {@link #isSuperAdmin(PermissionInfo)} tolerates
     * a null {@code pi}.
     */
    public boolean isSuperAdmin() {
        return roleCodes != null && roleCodes.contains(CODE_SUPER_ADMIN);
    }

    /** Static null-tolerant variant — {@code pi == null} treated as not super-admin. */
    public static boolean isSuperAdmin(PermissionInfo pi) {
        return pi != null && pi.isSuperAdmin();
    }
}
