package io.softa.starter.tenant.entitlement;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.EntitlementChangeMessage;

/**
 * Publishes {@link EntitlementChangeMessage} so the user-side consumer can clean up
 * over-plan role grants (v2 §2.2). No-op (logged) when {@code mq.topics.entitlement-change.topic}
 * is unconfigured — Pulsar is optional, and the in-process {@code EntitlementChangedListener}
 * has already refreshed the local plan snapshot + caches; MQ is only for the cross-service
 * grant cleanup.
 */
@Slf4j
@Component
public class EntitlementChangeProducer {

    @Value("${mq.topics.entitlement-change.topic:}")
    private String topic;

    private final PulsarTemplate<EntitlementChangeMessage> pulsarTemplate;

    public EntitlementChangeProducer(PulsarTemplate<EntitlementChangeMessage> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    public void publish(EntitlementChangeMessage message) {
        if (topic == null || topic.isBlank()) {
            log.debug("entitlement-change topic unconfigured; skipping MQ publish for tenant {}",
                    message.tenantId());
            return;
        }
        pulsarTemplate.sendAsync(topic, message).whenComplete((_, ex) -> {
            if (ex != null) {
                log.error("failed to publish entitlement-change for tenant {}", message.tenantId(), ex);
            }
        });
    }
}
