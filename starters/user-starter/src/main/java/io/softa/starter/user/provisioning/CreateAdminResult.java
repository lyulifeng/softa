package io.softa.starter.user.provisioning;

/**
 * Result of creating a tenant admin.
 *
 * @param userId the new admin's user id
 * @param email  the admin's email (= username)
 */
public record CreateAdminResult(Long userId, String email) {
}
