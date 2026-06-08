package io.softa.starter.ai.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * AI Message Role Enum
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "AI Message Role")
public enum AiMessageRole {
    @OptionItem(label = "User")
    USER("User"),
    @OptionItem(label = "Assistant")
    ASSISTANT("Assistant"),
    @OptionItem(label = "System")
    SYSTEM("System"),
    @OptionItem(label = "Tool")
    TOOL("Tool"),
    @OptionItem(label = "Function")
    FUNCTION("Function");

    @JsonValue
    private final String type;

}
