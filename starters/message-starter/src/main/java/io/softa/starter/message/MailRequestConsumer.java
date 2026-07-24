package io.softa.starter.message;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.MailRequestMessage;
import io.softa.starter.message.mail.dto.SendMailDTO;
import io.softa.starter.message.service.MessageService;

/**
 * Consumes {@link MailRequestMessage} off the mail-request MQ topic and delivers it through the mail
 * pipeline. Lets any starter request a <b>templated</b> mail without depending on message-starter (⊥):
 * the producer publishes to the topic, this consumer maps it to a {@link SendMailDTO} (template code +
 * variables) and hands it to {@link MessageService}, which renders the {@code MailTemplate} (system
 * {@code tenantId=0} fallback) and delivers via its outbox pipeline. Registered only when
 * {@code mq.topics.mail-request.topic} is configured (Pulsar optional) — same gating as the other
 * message consumers.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.topics.mail-request.topic")
public class MailRequestConsumer {

    private final MessageService messageService;

    public MailRequestConsumer(MessageService messageService) {
        this.messageService = messageService;
    }

    @PulsarListener(topics = "${mq.topics.mail-request.topic}",
            subscriptionName = "${mq.topics.mail-request.sub:mail-request-message}")
    public void onMessage(MailRequestMessage message) {
        if (message == null || message.to() == null || message.to().isEmpty()
                || message.templateCode() == null || message.templateCode().isBlank()) {
            return;
        }
        SendMailDTO mail = new SendMailDTO();
        mail.setTo(message.to());
        mail.setTemplateCode(message.templateCode());
        mail.setTemplateVariables(message.variables());
        messageService.sendMail(mail);
        log.debug("Delivered mail-request → template '{}' to {} recipient(s)",
                message.templateCode(), message.to().size());
    }
}
