package io.softa.framework.base.message;

import java.util.List;
import java.util.Map;

/**
 * A request to deliver a <b>templated</b> mail. Published by any starter that needs to notify a user
 * and consumed by message-starter — which renders {@code templateCode} with {@code variables} (its
 * {@code MailTemplate}) and delivers via its outbox/MQ pipeline.
 *
 * <p>Framework-level (and transport-agnostic) on purpose: the producer (e.g. user-starter) and the
 * consumer (message-starter) share this contract <b>without</b> a module dependency — they are ⊥ to
 * each other. Carried in-process as a Spring event within a transaction, then republished
 * AFTER_COMMIT onto the {@code mq.topics.mail-request} Pulsar topic; message-starter consumes it via
 * {@code @PulsarListener} (so it works whether message-starter is in-process or a separate service).
 *
 * <p><b>Scope of the render.</b> The MQ hop drops the producer's thread context, so tier selection
 * must travel in the message — every producer declares it explicitly (there is deliberately no
 * shorter constructor to default it silently). {@code tenantId} is the tenant the consumer restores
 * before rendering and persisting the send record; {@code scope} is the {@link MessageScope} tier
 * policy. A {@code TENANT}-scoped message must carry the tenant id in multi-tenant deployments —
 * the tenant's template is unreachable without its context.
 *
 * @param to           recipient addresses (at least one)
 * @param templateCode the {@code MailTemplate} business code to render
 * @param variables    placeholder values for the template ({@code {{ }}} substitution); may be empty
 * @param tenantId     tenant whose context the consumer restores for the render and record
 *                     ownership; null = no tenant context (single-tenant, or pure platform sends)
 * @param scope        tier-selection policy; null = {@link MessageScope#TENANT}
 */
public record MailRequestMessage(List<String> to, String templateCode, Map<String, Object> variables,
                                 Long tenantId, MessageScope scope) {
}
