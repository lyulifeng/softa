package io.softa.framework.base.message;

/**
 * MQ payload broadcast after a tenant's first admin account is created (and its transaction committed).
 * Business modules subscribe and decide for themselves what to attach to the admin — e.g. corehr creates
 * an {@code Employee} record bound to the admin's user account (linked via {@code userId}) so the admin
 * exists in the HR org as well. A framework DTO in {@code base.message} so producer (user-starter) and
 * consumers (any business module) share it without a module cycle.
 *
 * @param tenantId the tenant the admin belongs to
 * @param userId   the admin's user-account id (consumers bind their business record to this account)
 * @param email    the admin's email (business modules may use it as a display name / contact)
 * @param mobile   the admin's mobile (business modules may use it as a contact phone)
 */
public record AdminProvisionedMessage(Long tenantId, Long userId, String email, String mobile) {
}
