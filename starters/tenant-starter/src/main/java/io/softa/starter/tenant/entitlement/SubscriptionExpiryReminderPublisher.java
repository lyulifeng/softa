package io.softa.starter.tenant.entitlement;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import io.softa.framework.base.message.SubscriptionExpiryReminderMessage;

/**
 * Bridges the in-process {@link SubscriptionExpiryReminderEvent} to MQ so a user/business module can email
 * the tenant's admins. Fans a {@link SubscriptionExpiryReminderMessage} to Pulsar. The reminder job runs
 * outside a transaction, so {@code fallbackExecution = true} makes this fire immediately (there is nothing to
 * wait to commit). Mirrors {@code TenantProvisionedPublisher} — the {@code :}-empty-default topic gate makes
 * it a no-op when {@code mq.topics.subscription-expiry-reminder.topic} is unconfigured (tests / apps that
 * don't want reminder mails).
 */
@Slf4j
@Component
public class SubscriptionExpiryReminderPublisher {

    @Value("${mq.topics.subscription-expiry-reminder.topic:}")
    private String topic;

    private final PulsarTemplate<SubscriptionExpiryReminderMessage> pulsarTemplate;

    public SubscriptionExpiryReminderPublisher(PulsarTemplate<SubscriptionExpiryReminderMessage> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onExpiryReminder(SubscriptionExpiryReminderEvent event) {
        if (topic == null || topic.isBlank()) {
            log.debug("subscription-expiry-reminder topic unconfigured; skipping MQ publish for tenant {}",
                    event.tenantId());
            return;
        }
        SubscriptionExpiryReminderMessage message = new SubscriptionExpiryReminderMessage(
                event.tenantId(), event.tenantName(), event.planId(),
                event.effectiveTo() == null ? null : event.effectiveTo().toString(), event.daysLeft(),
                event.trial());
        pulsarTemplate.sendAsync(topic, message).whenComplete((__, ex) -> {
            if (ex != null) {
                log.error("failed to publish subscription-expiry-reminder for tenant {}", event.tenantId(), ex);
            }
        });
    }
}
