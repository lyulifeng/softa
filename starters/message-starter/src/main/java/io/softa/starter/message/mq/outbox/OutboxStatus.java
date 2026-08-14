package io.softa.starter.message.mq.outbox;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import io.softa.framework.base.enums.OptionItemTone;

/**
 * Lifecycle of an outbox entry.
 * <p>
 *   NEW → PUBLISHING         (claimed by OutboxPublisher)
 *   PUBLISHING → PUBLISHED   (happy path)
 *   PUBLISHING → NEW         (publish failed / stale claim recovered)
 *   PUBLISHING → DEAD        (exceeded max publish attempts)
 * <p>
 * {@code @OptionSet} is required, not decorative: {@code OutboxEntry.status} is an
 * OPTION field whose {@code optionSetCode} resolves to this enum's name, and reading
 * it with option expansion asserts the option set exists.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum OutboxStatus {
    @OptionItem(description = "Not yet published to broker", itemTone = OptionItemTone.NEUTRAL)
    NEW("New"),
    @OptionItem(description = "Claimed by a publisher instance", itemTone = OptionItemTone.INFO)
    PUBLISHING("Publishing"),
    @OptionItem(description = "Successfully published; kept for audit / replay",
            itemTone = OptionItemTone.SUCCESS)
    PUBLISHED("Published"),
    @OptionItem(description = "Broker failure beyond retry budget — needs manual intervention",
            itemTone = OptionItemTone.ERROR)
    DEAD("Dead");

    @JsonValue
    private final String code;
}
