package io.softa.starter.user.mail;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.MailRequestMessage;

/**
 * Republishes an in-process {@link MailRequestMessage} (fired by user-starter services within their
 * transaction) onto the {@code mq.topics.mail-request} Pulsar topic, <b>AFTER_COMMIT</b> — so the mail
 * is only enqueued once the business change that requested it (e.g. an invitation row) has committed,
 * and message-starter delivers it whether it runs in-process or as a separate service.
 *
 * <p>No message-starter dependency (user-starter ⊥ message-starter): they share only the framework
 * {@link MailRequestMessage} + the topic name. When the topic is unconfigured the publish is skipped
 * (graceful no-op — the business record still exists, so the mail can be re-requested / resent). Same
 * pattern as tenant-starter's entitlement-change producer.
 */
@Slf4j
@Component
public class MailRequestPublisher {

    @Value("${mq.topics.mail-request.topic:}")
    private String topic;

    private final PulsarTemplate<MailRequestMessage> pulsarTemplate;

    public MailRequestPublisher(PulsarTemplate<MailRequestMessage> pulsarTemplate) {
        this.pulsarTemplate = pulsarTemplate;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onMailRequested(MailRequestMessage message) {
        if (topic == null || topic.isBlank()) {
            log.debug("mail-request topic unconfigured; skipping MQ publish to {}", message.to());
            return;
        }
        pulsarTemplate.sendAsync(topic, message).whenComplete((__, ex) -> {
            if (ex != null) {
                log.error("failed to publish mail-request to {}", message.to(), ex);
            }
        });
    }
}
