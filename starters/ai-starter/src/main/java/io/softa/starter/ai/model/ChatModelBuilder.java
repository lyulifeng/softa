package io.softa.starter.ai.model;

import java.util.Set;
import org.springframework.ai.chat.model.ChatModel;

import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;

/**
 * SPI that builds a Spring AI {@link ChatModel} for a given {@link AiModel} row.
 * <p>
 * One implementation per provider family; the {@link ChatModelRegistry} dispatches
 * by {@link AiModelProvider} and caches the result. Mirrors the {@code TaskExecutor}
 * registry idiom in {@code flow-starter} (a bean per behavior, indexed by an enum).
 */
public interface ChatModelBuilder {

    /** Providers this builder can serve (a builder may cover several, e.g. the OpenAI family). */
    Set<AiModelProvider> supportedProviders();

    /**
     * Construct a {@link ChatModel} from a model row. Credentials, endpoint and the
     * default model code come from the {@link AiModel}; per-call generation params
     * (temperature, penalties, maxTokens) are applied later at call time.
     */
    ChatModel build(AiModel aiModel);
}
