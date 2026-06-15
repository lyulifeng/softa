package io.softa.framework.orm.service;

import java.util.List;

/**
 * Framework SPI for multi-tenant runtime concerns. Returns only ids / booleans — never the
 * tenant entity, which lives in tenant-starter (ADR-0017). The implementation
 * ({@code TenantInfoServiceImpl}) is provided by tenant-starter; framework consumers
 * (TenantAspect, ContextBuilder) depend only on this contract.
 */
public interface TenantInfoService {

    /**
     * Get all active tenant IDs.
     *
     * @return list of active tenant IDs
     */
    List<Long> getActiveTenantIds();

    /**
     * Whether the tenant is currently ACTIVE — the only state permitted to log in / operate.
     * Backed by the per-tenant cache, so it is cheap enough for a per-request gate.
     *
     * @param tenantId tenant id
     * @return true only if the tenant exists and its status is ACTIVE
     */
    boolean isTenantActive(Long tenantId);

    /**
     * Deactivate a tenant — the single sanctioned path out of ACTIVE: set its status to
     * SUSPENDED and evict the tenant caches so {@link #isTenantActive} and active-id
     * filtering reflect it immediately. Existing users are then forced to re-login on their
     * next request (the per-request tenant gate rejects them and drops their session).
     *
     * @param tenantId tenant id
     */
    void deactivate(Long tenantId);
}
