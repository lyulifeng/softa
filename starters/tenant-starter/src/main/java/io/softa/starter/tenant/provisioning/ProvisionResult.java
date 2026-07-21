package io.softa.starter.tenant.provisioning;

/**
 * Result of provisioning a tenant. The first admin is created separately (app-side), so this carries
 * only the new tenant's identity.
 *
 * @param tenantId the new tenant's id
 * @param code     the tenant code (as supplied, or slug-generated)
 */
public record ProvisionResult(Long tenantId, String code) {
}
