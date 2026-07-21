package io.softa.framework.base.message;

import java.util.List;
import java.util.Map;

/**
 * A request to deliver a <b>templated</b> mail. Published by any starter that needs to notify a user
 * and consumed by message-starter — which renders {@code templateCode} with {@code variables} (its
 * {@code MailTemplate}, falling back to the system {@code tenantId=0} template) and delivers via its
 * outbox/MQ pipeline.
 *
 * <p>Framework-level (and transport-agnostic) on purpose: the producer (e.g. user-starter) and the
 * consumer (message-starter) share this contract <b>without</b> a module dependency — they are ⊥ to
 * each other. Carried in-process as a Spring event within a transaction, then republished
 * AFTER_COMMIT onto the {@code mq.topics.mail-request} Pulsar topic; message-starter consumes it via
 * {@code @PulsarListener} (so it works whether message-starter is in-process or a separate service).
 *
 * @param to           recipient addresses (at least one)
 * @param templateCode the {@code MailTemplate} business code to render
 * @param variables    placeholder values for the template ({@code {{ }}} substitution); may be empty
 */
public record MailRequestMessage(List<String> to, String templateCode, Map<String, Object> variables) {
}
