package io.softa.framework.base.message;

import java.util.List;
import java.util.Map;

/**
 * A request to deliver a <b>templated</b> SMS. The exact counterpart of
 * {@link MailRequestMessage}, for the same reason and along the same path: any starter can ask for a
 * text message without depending on message-starter, which renders {@code templateCode} with
 * {@code variables} (its {@code SmsTemplate}, falling back to the system {@code tenantId=0} template),
 * routes to a provider by the recipient's dial code, and delivers through its own pipeline.
 *
 * <p>Carried in-process as a Spring event within a transaction, then republished AFTER_COMMIT onto the
 * {@code mq.topics.sms-request} Pulsar topic. Absent message-starter it is a graceful no-op — which is
 * what lets a caller notify over every channel a recipient has without knowing which are wired.
 *
 * <p><b>Numbers must carry their country dial code</b> ({@code +8613800138000}): provider selection is
 * by region, so a bare national number has no route.
 *
 * @param to           recipient mobile numbers, dial code included (at least one)
 * @param templateCode the {@code SmsTemplate} business code to render
 * @param variables    placeholder values for the template ({@code {{ }}} substitution); may be empty
 */
public record SmsRequestMessage(List<String> to, String templateCode, Map<String, Object> variables) {
}
