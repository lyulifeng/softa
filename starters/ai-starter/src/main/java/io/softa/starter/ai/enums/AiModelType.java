package io.softa.starter.ai.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * AI Model Type
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "AI Model Type")
public enum AiModelType {
    @OptionItem(label = "GPT")
    GPT("GPT"),
    @OptionItem(label = "Image Model")
    IMAGE("Image"),
    @OptionItem(label = "Audio Model")
    AUDIO("Audio"),
    @OptionItem(label = "Video Model")
    VIDEO("Video");

    @JsonValue
    private final String type;
}
