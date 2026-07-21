package io.softa.starter.user.provisioning;

/**
 * Published (inside the admin-creation transaction) right after a tenant's first admin account is created,
 * role-granted and invited by {@link AdminProvisioningService}. Lets business modules react — e.g. corehr
 * creates an {@code Employee} bound to the admin account — WITHOUT user-starter depending on them. Bridged
 * to MQ by {@code AdminProvisionedPublisher} on {@code AFTER_COMMIT}, so a rolled-back creation never
 * announces an admin that doesn't exist.
 *
 * @param tenantId the tenant the admin belongs to
 * @param userId   the admin's newly created user-account id
 * @param email    the admin's email
 * @param mobile   the admin's mobile (required at creation → downstream can use it as a contact phone)
 */
public record AdminProvisionedEvent(Long tenantId, Long userId, String email, String mobile) {
}
