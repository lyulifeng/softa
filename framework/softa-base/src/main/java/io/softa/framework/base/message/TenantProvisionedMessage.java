package io.softa.framework.base.message;

/**
 * MQ payload broadcast after a tenant is provisioned (and its transaction committed). Business modules
 * subscribe and decide for themselves what per-tenant data to seed — e.g. corehr creates the initial
 * legal entity / cost centre / department named after the tenant. A framework DTO in {@code base.message}
 * so producer (tenant-starter) and consumers (any business module) share it without a module cycle.
 *
 * @param tenantId the newly provisioned tenant's id
 * @param code     the tenant code (as supplied, or slug-generated)
 * @param name     the tenant display name (business modules name their seeded masters after it)
 */
public record TenantProvisionedMessage(Long tenantId, String code, String name) {
}
