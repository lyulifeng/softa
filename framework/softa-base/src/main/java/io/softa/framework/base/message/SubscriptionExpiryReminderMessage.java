package io.softa.framework.base.message;

/**
 * MQ payload broadcast when a tenant's subscription is approaching its expiry date and a reminder is due
 * (at the tenant's local reminder hour). A business/user module subscribes and decides who to notify —
 * e.g. user-starter emails every {@code TENANT_ADMIN} of the tenant. A framework DTO in {@code base.message}
 * so producer (tenant-starter) and consumers share it without a module cycle (tenant-starter ⊥ user-starter).
 *
 * <p>{@code effectiveTo} is an ISO-8601 date string (not {@code LocalDate}) to keep the wire payload free of
 * JSR-310 serializer assumptions in the MQ codec; consumers display it as-is or parse it.
 *
 * @param tenantId   the tenant whose subscription is expiring
 * @param tenantName the tenant display name (for the notification body)
 * @param planId     the current plan id/code (for the notification body)
 * @param effectiveTo the subscription end date, ISO-8601 (e.g. {@code 2026-07-28})
 * @param daysLeft   whole days remaining until {@code effectiveTo} in the tenant's timezone (0 = last day)
 * @param trial      {@code true} if the expiring subscription is a trial (lifecycle {@code TRIAL}) rather
 *                   than a purchased plan — consumers use it to pick trial-vs-renewal wording
 */
public record SubscriptionExpiryReminderMessage(Long tenantId, String tenantName, String planId,
                                                String effectiveTo, int daysLeft, boolean trial) {
}
