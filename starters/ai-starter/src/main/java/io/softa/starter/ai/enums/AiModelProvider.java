package io.softa.starter.ai.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * AI model provider.
 * <p>
 * Each value maps to a Spring AI model module. {@code OPEN_AI}, {@code AZURE_OPEN_AI}
 * and {@code OPENAI_COMPATIBLE} are all served by the OpenAI module (base-url driven);
 * {@code OPENAI_COMPATIBLE} covers any OpenAI-compatible endpoint (ChatGLM, Qwen,
 * Moonshot, ZhiPu, ...). {@code DEEP_SEEK} and {@code ANTHROPIC} use their dedicated
 * Spring AI modules.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "AI Model Provider")
public enum AiModelProvider {
    @OptionItem(label = "OpenAI")
    OPEN_AI("OpenAI"),
    @OptionItem(label = "Azure OpenAI")
    AZURE_OPEN_AI("AzureOpenAI"),
    @OptionItem(label = "OpenAI-Compatible")
    OPENAI_COMPATIBLE("OpenAICompatible"),
    @OptionItem(label = "DeepSeek")
    DEEP_SEEK("DeepSeek"),
    ANTHROPIC("Anthropic");

    @JsonValue
    private final String type;
}
