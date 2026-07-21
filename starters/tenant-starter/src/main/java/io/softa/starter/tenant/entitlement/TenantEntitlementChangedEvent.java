package io.softa.starter.tenant.entitlement;

/**
 * Published (within tenant-starter) whenever a tenant's entitlement changes — its owned
 * {@code TenantSubscription} (plan / lifecycle / effective dates) is updated. The listener
 * re-resolves the tenant's effective modules, evicts the {@code entl:} snapshot, and fans the
 * fresh module set to MQ for downstream role-grant cleanup.
 */
public record TenantEntitlementChangedEvent(Long tenantId) {
}
