package io.softa.starter.message;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.pulsar.core.PulsarTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.MailRequestMessage;

/**
 * Republishes an in-process {@link MailRequestMessage} (fired by ANY business module within its
 * transaction) onto the {@code mq.topics.mail-request} Pulsar topic, <b>AFTER_COMMIT</b> — so the mail
 * is only enqueued once the business change that requested it (e.g. an invitation row) has committed.
 *
 * <p>Lives in message-starter so every service that can request mail carries its own publisher —
 * business modules publish the framework event ({@code softa-base}) and depend on nothing else.
 * It used to live in user-starter, which made mail publication silently depend on a deployment
 * happening to include user-starter — a trap once services are split.
 *
 * <p>Role split by configuration: a PUBLISHING service configures only
 * {@code mq.topics.mail-request.topic}; the DELIVERING service also configures
 * {@code mq.topics.mail-request.sub}, which is what gates {@link MailRequestConsumer}. When the
 * topic is unconfigured the publish is skipped (graceful no-op — the business record still exists,
 * so the mail can be re-requested / resent).
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
