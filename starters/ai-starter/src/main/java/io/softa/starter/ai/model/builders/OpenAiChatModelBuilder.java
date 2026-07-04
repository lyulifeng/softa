package io.softa.starter.ai.model.builders;

import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;

import io.softa.framework.base.utils.Assert;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;
import io.softa.starter.ai.model.AiObservability;
import io.softa.starter.ai.model.ChatModelBuilder;

/**
 * Builds an {@link OpenAiChatModel} for the OpenAI family. Serves plain OpenAI,
 * Azure OpenAI, and any OpenAI-compatible endpoint (ChatGLM / Qwen / Moonshot / ...)
 * via a custom base URL. Credentials live on {@link OpenAiChatOptions} in Spring AI 2.0.
 */
@Component
@RequiredArgsConstructor
public class OpenAiChatModelBuilder implements ChatModelBuilder {

    private final AiObservability aiObservability;

    @Override
    public Set<AiModelProvider> supportedProviders() {
        return EnumSet.of(AiModelProvider.OPEN_AI, AiModelProvider.AZURE_OPEN_AI, AiModelProvider.OPENAI_COMPATIBLE);
    }

    @Override
    public ChatModel build(AiModel aiModel) {
        Assert.notBlank(aiModel.getApiKey(), "API key cannot be empty for model: " + aiModel.getCode());
        if (aiModel.getModelProvider() != AiModelProvider.OPEN_AI) {
            Assert.notBlank(aiModel.getBaseUrl(),
                    "Base URL is required for provider " + aiModel.getModelProvider() + " (model: " + aiModel.getCode() + ")");
        }

        OpenAiChatOptions.Builder options = OpenAiChatOptions.builder();
        options.apiKey(aiModel.getApiKey());
        options.model(aiModel.getCode());
        if (StringUtils.isNotBlank(aiModel.getBaseUrl())) {
            options.baseUrl(aiModel.getBaseUrl());
        }
        if (aiModel.getTimeout() != null && aiModel.getTimeout() > 0) {
            options.timeout(Duration.ofMillis(aiModel.getTimeout()));
        }
        if (aiModel.getModelProvider() == AiModelProvider.AZURE_OPEN_AI) {
            options.azure(true);
            options.deploymentName(aiModel.getCode());
        }

        return OpenAiChatModel.builder()
                .options(options.build())
                .observationRegistry(aiObservability.registry())
                .build();
    }
}
