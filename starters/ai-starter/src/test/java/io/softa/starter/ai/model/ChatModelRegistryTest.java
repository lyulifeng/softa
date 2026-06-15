package io.softa.starter.ai.model;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import io.micrometer.observation.ObservationRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.model.ChatModel;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.enums.AiModelProvider;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ChatModelRegistry} — provider dispatch + per-row caching/invalidation.
 */
class ChatModelRegistryTest {

    /** A recording builder that returns a fresh mock ChatModel on each build. */
    static class RecordingBuilder implements ChatModelBuilder {
        final Set<AiModelProvider> providers;
        final AtomicInteger buildCount = new AtomicInteger();

        RecordingBuilder(Set<AiModelProvider> providers) {
            this.providers = providers;
        }

        @Override
        public Set<AiModelProvider> supportedProviders() {
            return providers;
        }

        @Override
        public ChatModel build(AiModel aiModel) {
            buildCount.incrementAndGet();
            return mock(ChatModel.class);
        }
    }

    private static AiModel model(Long id, AiModelProvider provider, LocalDateTime updatedTime) {
        AiModel m = new AiModel();
        m.setId(id);
        m.setCode("gpt-test");
        m.setModelProvider(provider);
        m.setUpdatedTime(updatedTime);
        return m;
    }

    @Test
    void dispatchesByProviderAndCachesByUpdatedTime() {
        RecordingBuilder openai = new RecordingBuilder(
                Set.of(AiModelProvider.OPEN_AI, AiModelProvider.OPENAI_COMPATIBLE));
        ChatModelRegistry registry = new ChatModelRegistry(List.of(openai), mock(AiObservability.class));

        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        AiModel m = model(1L, AiModelProvider.OPEN_AI, t);

        ChatModel first = registry.get(m);
        ChatModel second = registry.get(model(1L, AiModelProvider.OPEN_AI, t));

        assertThat(first).isSameAs(second);
        assertThat(openai.buildCount.get()).isEqualTo(1);
    }

    @Test
    void rebuildsWhenUpdatedTimeChanges() {
        RecordingBuilder openai = new RecordingBuilder(Set.of(AiModelProvider.OPEN_AI));
        ChatModelRegistry registry = new ChatModelRegistry(List.of(openai), mock(AiObservability.class));

        ChatModel v1 = registry.get(model(1L, AiModelProvider.OPEN_AI, LocalDateTime.of(2026, 1, 1, 0, 0)));
        ChatModel v2 = registry.get(model(1L, AiModelProvider.OPEN_AI, LocalDateTime.of(2026, 1, 2, 0, 0)));

        assertThat(v1).isNotSameAs(v2);
        assertThat(openai.buildCount.get()).isEqualTo(2);
    }

    @Test
    void openAiBuilderServesOpenAiCompatibleFamily() {
        RecordingBuilder openai = new RecordingBuilder(
                Set.of(AiModelProvider.OPEN_AI, AiModelProvider.OPENAI_COMPATIBLE));
        ChatModelRegistry registry = new ChatModelRegistry(List.of(openai), mock(AiObservability.class));

        ChatModel m = registry.get(model(9L, AiModelProvider.OPENAI_COMPATIBLE, LocalDateTime.of(2026, 1, 1, 0, 0)));

        assertThat(m).isNotNull();
        assertThat(openai.buildCount.get()).isEqualTo(1);
    }

    @Test
    void unsupportedProviderThrows() {
        RecordingBuilder openai = new RecordingBuilder(Set.of(AiModelProvider.OPEN_AI));
        ChatModelRegistry registry = new ChatModelRegistry(List.of(openai), mock(AiObservability.class));

        assertThatThrownBy(() -> registry.get(model(2L, AiModelProvider.ANTHROPIC, LocalDateTime.of(2026, 1, 1, 0, 0))))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not supported");
    }

    @Test
    void getChatClient_cachesPerModel_andRebuildsOnUpdatedTimeChange() {
        RecordingBuilder openai = new RecordingBuilder(Set.of(AiModelProvider.OPEN_AI));
        AiObservability obs = mock(AiObservability.class);
        when(obs.registry()).thenReturn(ObservationRegistry.NOOP);
        ChatModelRegistry registry = new ChatModelRegistry(List.of(openai), obs);

        LocalDateTime t = LocalDateTime.of(2026, 1, 1, 0, 0);
        ChatClient c1 = registry.getChatClient(model(1L, AiModelProvider.OPEN_AI, t));
        ChatClient c2 = registry.getChatClient(model(1L, AiModelProvider.OPEN_AI, t));
        assertThat(c1).isSameAs(c2);                       // client cached + reused
        assertThat(openai.buildCount.get()).isEqualTo(1);  // underlying model built once

        ChatClient c3 = registry.getChatClient(
                model(1L, AiModelProvider.OPEN_AI, LocalDateTime.of(2026, 1, 2, 0, 0)));
        assertThat(c3).isNotSameAs(c1);                    // rebuilt on updatedTime change
        assertThat(openai.buildCount.get()).isEqualTo(2);
    }
}
