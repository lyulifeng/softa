package io.softa.starter.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.SmsRequestMessage;

/**
 * Republishes an in-process {@link SmsRequestMessage} (fired by ANY business module within its
 * transaction) onto the {@code mq.topics.sms-request} Pulsar topic, <b>AFTER_COMMIT</b> — so the text
 * is only enqueued once the business change that requested it has committed.
 *
 * <p>The exact counterpart of {@link MailRequestPublisher}; see it for why the publisher lives here
 * rather than in the requesting module, and for the publish/deliver role split by configuration
 * ({@code .topic} on the publisher, {@code .sub} on the deliverer). An unconfigured topic skips the
 * publish — a graceful no-op that lets a caller fan an invitation out to every channel a recipient
 * has without knowing which transports this deployment actually wires up.
 */
@Slf4j
@Component
public class SmsRequestPublisher {

    @Value("${mq.topics.sms-request.topic:}")
    private String topic;

    private final PulsarTemplate<SmsRequestMessage> pulsarTemplate;

    public SmsRequestPublisher(PulsarTemplate<SmsRequestMessage> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onSmsRequested(SmsRequestMessage message) {
        if (topic == null || topic.isBlank()) {
            log.debug("sms-request topic unconfigured; skipping MQ publish to {}", message.to());
            return;
        }
        pulsarTemplate.sendAsync(topic, message).whenComplete((__, ex) -> {
            if (ex != null) {
                log.error("failed to publish sms-request to {}", message.to(), ex);
            }
        });
    }
}
