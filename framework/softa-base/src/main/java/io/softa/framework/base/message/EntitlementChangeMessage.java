package io.softa.framework.base.message;

import java.util.Set;

/**
 * MQ payload for a tenant entitlement change (版本计费 v2 §2.2): the tenant whose plan
 * changed + its new effective module set. Lives in the framework so the tenant-side
 * producer and the user-side consumer can share the contract without either starter
 * depending on the other (same ⊥ rationale as the {@code EntitlementService} SPI).
 *
 * <p>The consumer (user-starter) uses it to physically remove role-navigation grants whose
 * module is no longer entitled, then evicts the affected users' permission snapshots.
 *
 * @param tenantId        the tenant whose plan changed
 * @param entitledModules the tenant's new effective module-id set
 */
public record EntitlementChangeMessage(Long tenantId, Set<String> entitledModules) {
}
