package io.softa.starter.message.dlq.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Lifecycle of a {@code dead_letter_message} row. Carries {@code @OptionSet} because
 * {@code DeadLetterMessage.status} is an OPTION field and the triage UI renders it
 * through the generic metadata surface.
 *
 * <ul>
 *   <li>{@link #PENDING}: just landed; awaits human triage</li>
 *   <li>{@link #RESOLVED}: business compensation done, closed</li>
 *   <li>{@link #DISCARDED}: not worth compensating, closed</li>
 * </ul>
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum DeadLetterStatus {
    PENDING("Pending"),
    RESOLVED("Resolved"),
    DISCARDED("Discarded");

    @JsonValue
    private final String code;
}
