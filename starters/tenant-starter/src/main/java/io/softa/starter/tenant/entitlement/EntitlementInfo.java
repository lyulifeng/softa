package io.softa.starter.tenant.entitlement;

import java.util.Set;

/**
 * Resolved per-tenant entitlement — cached in Redis under {@code entl:{tenantId}}. {@code planId} is
 * the tenant's effective plan id (the <b>floor plan</b> when unsubscribed / expired / fail-closed, or
 * {@code null} when the catalog has no plans at all); {@code tier} is that plan's tier;
 * {@code entitledModuleIds} is the effective module set (the plan's plan_entitlement modules,
 * fail-closed to the floor plan's set — which may legitimately be empty, see
 * {@code EntitlementService#entitledModules}).
 */
public record EntitlementInfo(String planId, Integer tier, Set<String> entitledModuleIds) {
}
