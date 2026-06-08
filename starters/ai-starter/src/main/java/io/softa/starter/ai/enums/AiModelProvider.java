package io.softa.starter.ai.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * AI model provider.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "AI Model Provider")
public enum AiModelProvider {
    @OptionItem(label = "OpenAI")
    OPEN_AI("OpenAI"),
    @OptionItem(label = "DeepSeek")
    DEEP_SEEK("DeepSeek"),
    @OptionItem(label = "Azure OpenAI")
    AZURE_OPEN_AI("AzureOpenAI"),
    @OptionItem(label = "ChatGLM")
    CHAT_GLM("ChatGLM");

    @JsonValue
    private final String type;
}
