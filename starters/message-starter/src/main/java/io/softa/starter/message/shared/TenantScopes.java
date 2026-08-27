package io.softa.starter.message.shared;

import java.util.List;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;

/**
 * Tenant visibility scopes for platform-overlay reads.
 * <p>
 * Messaging config tables follow a shared-plus-overlay model: {@code tenant_id
 * = 0} rows are platform-level and visible to every tenant, {@code > 0} rows
 * belong to one tenant. Overlay reads therefore run {@code @CrossTenant} (to
 * suppress the implicit single-tenant filter) and constrain explicitly to
 * {@code tenantId IN (0, currentTenant)} via this helper.
 * <p>
 * This class is the single home of the {@code 0} sentinel — services compare
 * against {@link #PLATFORM} instead of a literal.
 */
public final class TenantScopes {

    /** The platform tier's tenant id — rows shared across all tenants. */
    public static final long PLATFORM = 0L;

    private TenantScopes() {}

    /**
     * Whether multi-tenancy is enabled for this deployment. Null-safe against
     * a missing {@link SystemConfig} (plain unit tests), where it reports
     * {@code false} — the single-tenant behaviour.
     */
    public static boolean multiTenancyEnabled() {
        return SystemConfig.env != null && SystemConfig.env.isEnableMultiTenancy();
    }

    /**
     * The current caller's tenant id, or {@link #PLATFORM} when the context
     * carries none (platform console, background jobs, single-tenant).
     */
    public static long currentTenantOrPlatform() {
        Context ctx = ContextHolder.getContext();
        Long tenant = ctx != null ? ctx.getTenantId() : null;
        return tenant == null ? PLATFORM : tenant;
    }

    /**
     * The tenants visible to the current caller: the platform tier (0) plus
     * the caller's own tenant when one is set. Safe in single-tenant
     * deployments where the context carries no tenant.
     */
    public static List<Long> currentPlusPlatform() {
        long tenant = currentTenantOrPlatform();
        return tenant == PLATFORM ? List.of(PLATFORM) : List.of(PLATFORM, tenant);
    }
}
