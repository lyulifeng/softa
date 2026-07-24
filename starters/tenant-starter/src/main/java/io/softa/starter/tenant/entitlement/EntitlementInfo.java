package io.softa.starter.tenant.entitlement;

import java.util.Set;

/**
 * Resolved per-tenant entitlement — cached in Redis under {@code entl:{tenantId}}. {@code planId} is
 * the tenant's effective plan id (FREE when unsubscribed / expired / fail-closed); {@code tier} is that
 * plan's tier; {@code entitledModuleIds} is the effective module set (the plan's plan_entitlement
 * modules, fail-closed to the FREE base set).
 */
public record EntitlementInfo(String planId, Integer tier, Set<String> entitledModuleIds) {
}
