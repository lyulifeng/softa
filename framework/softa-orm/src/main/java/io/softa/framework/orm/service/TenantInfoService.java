package io.softa.framework.orm.service;

import java.util.List;

/**
 * Framework SPI for multi-tenant runtime concerns. Returns only ids / booleans — never the
 * tenant entity, which lives in tenant-starter. The implementation
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
     * Suspend a tenant — ACTIVE → SUSPENDED. See {@link #activate} for the contract these three share.
     *
     * @param tenantId tenant id
     */
    void deactivate(Long tenantId);

    /**
     * Reactivate a suspended tenant — SUSPENDED → ACTIVE.
     *
     * <p>These three transitions ({@code activate} / {@link #deactivate} / {@link #close}) are the
     * <b>only</b> sanctioned way to change a tenant's operational status, because each of them does two
     * things that a plain column write does not:
     *
     * <ol>
     *   <li>stamps the timestamp belonging to the target status and <b>clears the other two</b>, so exactly
     *       one is ever set and the trio stays a function of the status — an active tenant can never show a
     *       suspended time;</li>
     *   <li>evicts the tenant caches, so {@link #isTenantActive} and active-id filtering see the change at
     *       once. Users of a tenant leaving ACTIVE are then forced to re-login on their next request (the
     *       per-request gate rejects them and drops their session).</li>
     * </ol>
     *
     * Without the eviction a suspension does not take effect until the cache expires — the tenant's users
     * keep working meanwhile — which is why the status column must not be exposed as an editable field.
     *
     * @param tenantId tenant id
     */
    void activate(Long tenantId);

    /**
     * Close a tenant — ACTIVE / SUSPENDED → CLOSED. Terminal; data is retained. See {@link #activate} for
     * the shared contract.
     *
     * @param tenantId tenant id
     */
    void close(Long tenantId);
}
