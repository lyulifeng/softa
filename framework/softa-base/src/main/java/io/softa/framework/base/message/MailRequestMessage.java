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
 * must travel in the message. {@code tenantId} is the tenant the consumer restores before rendering
 * (letting {@code scope = OVERLAY} apply that tenant's template/server customizations); {@code scope}
 * is the {@link MailScope} tier policy. Both {@code null} keeps the historical behaviour — the render
 * runs with no tenant context and resolves the platform tier ({@code tenant_id = 0}) only. Producers
 * that want the tenant's branding must pass the tenant id explicitly via the 5-arg constructor.
 *
 * @param to           recipient addresses (at least one)
 * @param templateCode the {@code MailTemplate} business code to render
 * @param variables    placeholder values for the template ({@code {{ }}} substitution); may be empty
 * @param tenantId     tenant whose context the consumer restores for the render; null = no tenant
 *                     context (platform-tier render, the pre-scope behaviour)
 * @param scope        tier-selection policy; null = {@link MailScope#OVERLAY}
 */
public record MailRequestMessage(List<String> to, String templateCode, Map<String, Object> variables,
                                 Long tenantId, MailScope scope) {

    /**
     * Compatibility constructor — platform-tier render with no tenant context,
     * exactly the behaviour before {@code tenantId} / {@code scope} existed.
     */
    public MailRequestMessage(List<String> to, String templateCode, Map<String, Object> variables) {
        this(to, templateCode, variables, null, null);
    }
}
