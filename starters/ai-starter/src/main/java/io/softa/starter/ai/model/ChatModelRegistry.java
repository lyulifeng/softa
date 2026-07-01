package io.softa.starter.ai.model;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import lombok.extern.slf4j.Slf4j;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.Assert;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;

/**
 * Resolves a Spring AI {@link ChatModel} per {@link AiModel} row (built on demand) and caches the
 * reusable {@link ChatClient} per row.
 * <p>
 * Replaces the former {@code AiAdapterFactory}. Dispatch is by {@link AiModelProvider}
 * over the registered {@link ChatModelBuilder} beans. The client cache key includes the row's
 * {@code updatedTime}, so editing a model (new credentials, endpoint, code) yields a new key and a
 * freshly built client; stale versions for the same id are evicted. The model is <b>not</b> cached
 * separately: {@link #get(AiModel)} has no caller beyond {@link #getChatClient(AiModel)}, whose
 * client cache already covers the reuse, so a model cache could never serve a hit.
 */
@Slf4j
@Component
public class ChatModelRegistry {

    private final Map<AiModelProvider, ChatModelBuilder> builders;
    private final AiObservability aiObservability;
    private final Map<String, ChatClient> clientCache = new ConcurrentHashMap<>();

    public ChatModelRegistry(List<ChatModelBuilder> chatModelBuilders, AiObservability aiObservability) {
        Map<AiModelProvider, ChatModelBuilder> map = new EnumMap<>(AiModelProvider.class);
        for (ChatModelBuilder builder : chatModelBuilders) {
            for (AiModelProvider provider : builder.supportedProviders()) {
                map.put(provider, builder);
            }
        }
        this.builders = map;
        this.aiObservability = aiObservability;
        log.info("ChatModelRegistry initialized for providers: {}", map.keySet());
    }

    /**
     * Resolve and build the {@link ChatModel} for a model row. Built on demand (not cached) — the
     * only caller, {@link #getChatClient(AiModel)}, caches the wrapping client.
     */
    public ChatModel get(AiModel aiModel) {
        Assert.notNull(aiModel, "AI model cannot be null");
        AiModelProvider provider = aiModel.getModelProvider();
        Assert.notNull(provider, "AI model provider cannot be empty for model: " + aiModel.getCode());

        ChatModelBuilder builder = builders.get(provider);
        if (builder == null) {
            throw new IllegalArgumentException("AI model provider not supported: " + provider);
        }
        return builder.build(aiModel);
    }

    /**
     * Get (building and caching on first use) a reusable {@link ChatClient} for a model row.
     * Built with the application's ObservationRegistry so AI calls stay observable — a bare
     * {@code ChatClient.create(model)} bypasses observability. Cached and reused; editing the
     * model row (new {@code updatedTime}) rebuilds it and evicts the prior version.
     */
    public ChatClient getChatClient(AiModel aiModel) {
        String key = cacheKey(aiModel);
        ChatClient cached = clientCache.get(key);
        if (cached != null) {
            return cached;
        }
        ChatClient client = ChatClient.create(get(aiModel), aiObservability.registry());
        clientCache.keySet().removeIf(k -> k.startsWith(aiModel.getId() + ":"));
        clientCache.put(key, client);
        return client;
    }

    private String cacheKey(AiModel aiModel) {
        return aiModel.getId() + ":" + (aiModel.getUpdatedTime() == null ? "0" : aiModel.getUpdatedTime());
    }
}
