package io.softa.starter.message.dlq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Origin of a {@code dead_letter_message} row — the unified dead-letter store
 * has two independent feeders. Carries {@code @OptionSet} because
 * {@code DeadLetterMessage.source} is an OPTION field.
 *
 * <ul>
 *   <li>{@link #BROKER_POISON}: a Pulsar consumer could not process a message
 *       after the broker's max redeliveries; the raw envelope was routed to the
 *       DLQ topic and archived by {@code DeadLetterConsumer}.</li>
 *   <li>{@link #SEND_EXHAUSTED}: a mail / SMS send record exhausted its provider
 *       retry budget and was marked {@code DEAD_LETTER} by
 *       {@code SendFailureHandler}.</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum DeadLetterSource {
    BROKER_POISON("BrokerPoison"),
    SEND_EXHAUSTED("SendExhausted");

    @JsonValue
    private final String code;
}
