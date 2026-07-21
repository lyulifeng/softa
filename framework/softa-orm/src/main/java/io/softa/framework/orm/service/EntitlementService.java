package io.softa.framework.orm.service;

import java.util.Set;

/**
 * Framework SPI for plan-based entitlement (版本计费). Returns the module ids a tenant's
 * current plan (+ overrides) entitles it to — never plan / subscription entities, which
 * live in tenant-starter. The implementation ({@code EntitlementServiceImpl}) is provided by
 * tenant-starter; consumers depend only on this contract and inject it
 * {@code @Autowired(required = false)} — an absent impl (tenant-starter not installed) is
 * {@code null} = pure RBAC, no entitlement gating. Same SPI-in-framework / impl-in-starter
 * pattern as {@link TenantInfoService}; the two starters never depend on each other.
 *
 * <p>A module id is the first segment of a navigation id ({@code navigation.<module>.…}),
 * e.g. {@code core-hr} / {@code attendance} / {@code ai}.
 */
public interface EntitlementService {

    /**
     * The module ids the tenant is currently entitled to: plan modules ∪ active GRANT
     * overrides − active REVOKE overrides. Fail-closed — an unknown / unconfigured /
     * suspended tenant resolves to the free plan's base module set, never an empty set.
     *
     * @param tenantId tenant id
     * @return entitled module ids (never {@code null})
     */
    Set<String> entitledModules(Long tenantId);
}
