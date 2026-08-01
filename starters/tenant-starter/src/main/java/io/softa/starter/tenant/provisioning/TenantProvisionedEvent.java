package io.softa.starter.tenant.provisioning;

/**
 * Published (synchronously, inside the provisioning transaction) right after a tenant's registry row +
 * owned subscription are created by {@link TenantProvisioningService}. Lets the app react atomically —
 * e.g. seed per-tenant pre-data and provision the first admin — WITHOUT tenant-starter depending on
 * metadata-starter / user-starter (both ⊥ to it). Because it fires synchronously within the same
 * transaction, a listener that throws rolls the whole provisioning back.
 *
 * @param tenantId the newly created tenant's id
 * @param code     the tenant code (as supplied, or slug-generated)
 * @param name     the tenant display name (carried so downstream MQ consumers can name seeded masters)
 * @param rebuild  true only when this is a deliberate restart of a tenant's setup, false on first
 *                 provisioning. Seeders use it to decide whether to discard their previous output before
 *                 seeding: on a first provision there is none, and on an ordinary redelivery discarding would
 *                 destroy a committed result that is already correct — the flag is what separates
 *                 "somebody asked for this to be rebuilt" from "this message arrived twice", which the
 *                 seeder cannot otherwise tell apart
 */
public record TenantProvisionedEvent(Long tenantId, String code, String name, boolean rebuild) {
}
