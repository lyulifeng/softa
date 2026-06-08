package io.softa.starter.ai.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * AI Message Status Enum
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "AI Message Status")
public enum AiMessageStatus {
    @OptionItem(label = "Pending")
    PENDING("Pending"),
    @OptionItem(label = "Interrupted")
    INTERRUPTED("Interrupted"),
    @OptionItem(label = "Completed")
    COMPLETED("Completed"),
    @OptionItem(label = "Failed")
    FAILED("Failed");

    @JsonValue
    private final String type;

}
