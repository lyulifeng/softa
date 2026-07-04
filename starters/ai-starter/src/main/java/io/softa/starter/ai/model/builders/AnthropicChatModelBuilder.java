package io.softa.starter.ai.model.builders;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.anthropic.AnthropicChatModel;
import org.springframework.ai.anthropic.AnthropicChatOptions;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import io.softa.framework.base.utils.Assert;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;
import io.softa.starter.ai.model.AiObservability;
import io.softa.starter.ai.model.ChatModelBuilder;

/**
 * Builds an {@link AnthropicChatModel} via the official anthropic-java SDK.
 * Credentials live on {@link AnthropicChatOptions}. Anthropic requires {@code maxTokens};
 * a sane default is set here and overridden per-call by {@code AiRobot.outputTokensLimit}.
 */
@Component
@RequiredArgsConstructor
public class AnthropicChatModelBuilder implements ChatModelBuilder {

    /** Anthropic requires an explicit output token cap; Spring AI's own default. */
    private static final int DEFAULT_MAX_TOKENS = 4096;

    private final AiObservability aiObservability;

    @Override
    public Set<AiModelProvider> supportedProviders() {
        return Set.of(AiModelProvider.ANTHROPIC);
    }

    @Override
    public ChatModel build(AiModel aiModel) {
        Assert.notBlank(aiModel.getApiKey(), "API key cannot be empty for model: " + aiModel.getCode());

        AnthropicChatOptions.Builder options = AnthropicChatOptions.builder();
        options.apiKey(aiModel.getApiKey());
        options.model(aiModel.getCode());
        options.maxTokens(DEFAULT_MAX_TOKENS);
        if (StringUtils.isNotBlank(aiModel.getBaseUrl())) {
            options.baseUrl(aiModel.getBaseUrl());
        }

        return AnthropicChatModel.builder()
                .options(options.build())
                .observationRegistry(aiObservability.registry())
                .build();
    }
}
