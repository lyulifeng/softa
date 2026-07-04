package io.softa.starter.ai.model.builders;

import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.deepseek.DeepSeekChatModel;
import org.springframework.ai.deepseek.DeepSeekChatOptions;
import org.springframework.ai.deepseek.api.DeepSeekApi;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import io.softa.framework.base.utils.Assert;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;
import io.softa.starter.ai.model.AiObservability;
import io.softa.starter.ai.model.ChatModelBuilder;

/**
 * Builds a {@link DeepSeekChatModel}. Unlike OpenAI/Anthropic, the DeepSeek module
 * carries credentials on a {@link DeepSeekApi} object; the model code is set as a
 * String on the portable options (overriding the dedicated enum overload).
 */
@Component
@RequiredArgsConstructor
public class DeepSeekChatModelBuilder implements ChatModelBuilder {

    private final AiObservability aiObservability;

    @Override
    public Set<AiModelProvider> supportedProviders() {
        return Set.of(AiModelProvider.DEEP_SEEK);
    }

    @Override
    public ChatModel build(AiModel aiModel) {
        Assert.notBlank(aiModel.getApiKey(), "API key cannot be empty for model: " + aiModel.getCode());

        DeepSeekApi.Builder api = DeepSeekApi.builder();
        api.apiKey(aiModel.getApiKey());
        if (StringUtils.isNotBlank(aiModel.getBaseUrl())) {
            api.baseUrl(aiModel.getBaseUrl());
        }

        DeepSeekChatOptions.Builder options = DeepSeekChatOptions.builder();
        options.model(aiModel.getCode());

        return DeepSeekChatModel.builder()
                .deepSeekApi(api.build())
                .options(options.build())
                .observationRegistry(aiObservability.registry())
                .build();
    }
}
