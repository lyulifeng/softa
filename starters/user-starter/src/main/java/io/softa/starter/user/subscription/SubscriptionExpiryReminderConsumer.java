package io.softa.starter.user.subscription;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import static io.softa.framework.base.context.ContextUtils.inTenantContext;
import io.softa.framework.base.message.SubscriptionExpiryReminderMessage;

/**
 * user-starter's subscriber to the subscription-expiry-reminder MQ message published by tenant-starter's
 * {@code SubscriptionExpiryJob} when a tenant's subscription nears its end date (at the tenant-local reminder
 * hour). Emails every {@code TENANT_ADMIN} via {@link SubscriptionExpiryReminderMailer}, run in the tenant's
 * own context. Gated by {@code mq.topics.subscription-expiry-reminder.topic} (bean absent when unconfigured).
 * Shared subscription so it scales across app instances; each reminder point is emitted once per day by the
 * hourly cron's tenant-local-hour gate.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.topics.subscription-expiry-reminder.topic")
public class SubscriptionExpiryReminderConsumer {

    private final SubscriptionExpiryReminderMailer mailer;

    public SubscriptionExpiryReminderConsumer(SubscriptionExpiryReminderMailer mailer) {
        this.mailer = mailer;
    }

    @PulsarListener(topics = "${mq.topics.subscription-expiry-reminder.topic}",
            subscriptionName = "${mq.topics.subscription-expiry-reminder.sub:subscription-expiry-reminder}",
            subscriptionType = SubscriptionType.Shared)
    public void onExpiryReminder(SubscriptionExpiryReminderMessage message) {
        if (message == null || message.tenantId() == null) {
            return;
        }
        log.info("Received subscription-expiry reminder for tenant {} ({} day(s) left) — notifying admins",
                message.tenantId(), message.daysLeft());
        inTenantContext(message.tenantId(), () -> mailer.remindAdmins(message));
    }
}
