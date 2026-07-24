package io.softa.starter.tenant.entitlement;

import java.time.LocalDate;

/**
 * Published (in-process) by {@link SubscriptionExpiryJob} when a tenant's active subscription is a
 * configured number of days from its {@code effectiveTo} AND it is currently the tenant-local reminder
 * hour — one event per due tenant per reminder point. Bridged to MQ by
 * {@code SubscriptionExpiryReminderPublisher} so a user/business module can email the tenant's admins,
 * without tenant-starter depending on user-starter (⊥). Carries {@link LocalDate} in-process; the publisher
 * renders it to an ISO string on the wire.
 *
 * @param tenantId    the tenant whose subscription is expiring
 * @param tenantName  the tenant display name
 * @param planId      the current plan id/code
 * @param effectiveTo the subscription end date (tenant-local)
 * @param daysLeft    whole days remaining until {@code effectiveTo} in the tenant's timezone (0 = last day)
 * @param trial       {@code true} if the expiring subscription's lifecycle is {@code TRIAL} (vs a purchased
 *                    plan) — carried so the notifier can pick trial-vs-renewal wording
 */
public record SubscriptionExpiryReminderEvent(Long tenantId, String tenantName, String planId,
                                              LocalDate effectiveTo, int daysLeft, boolean trial) {
}
