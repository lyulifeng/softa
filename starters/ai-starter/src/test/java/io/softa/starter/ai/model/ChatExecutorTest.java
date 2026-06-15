package io.softa.starter.ai.model;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.metadata.ChatResponseMetadata;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;
import reactor.core.publisher.Flux;

import io.softa.starter.ai.constant.AiConstant;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.entity.AiRobot;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests for {@link ChatExecutor}. Uses a fake {@link ChatModel} (single abstract method)
 * so the call/stream paths run through a real {@link org.springframework.ai.chat.client.ChatClient}.
 */
class ChatExecutorTest {

    private static ChatResponse response(AssistantMessage message, ChatResponseMetadata metadata) {
        return metadata == null
                ? new ChatResponse(List.of(new Generation(message)))
                : new ChatResponse(List.of(new Generation(message)), metadata);
    }

    /**
     * Minimal fake ChatModel that returns a canned sync response and a fixed stream.
     * Only {@code call} (the sole abstract method) and {@code stream} (its default
     * throws {@code UnsupportedOperationException}) need overriding; the default
     * {@code getOptions()} already returns empty portable options.
     */
    private static ChatModel fakeModel(ChatResponse syncResponse, List<ChatResponse> streamChunks) {
        return new ChatModel() {
            @Override
            public ChatResponse call(Prompt prompt) {
                return syncResponse;
            }

            @Override
            public Flux<ChatResponse> stream(Prompt prompt) {
                return Flux.fromIterable(streamChunks);
            }
        };
    }

    private static ChatExecutor executorFor(ChatModel model) {
        ChatModelRegistry registry = mock(ChatModelRegistry.class);
        when(registry.getChatClient(any(AiModel.class))).thenReturn(ChatClient.create(model));
        return new ChatExecutor(registry);
    }

    private static AiRobot robot() {
        AiRobot robot = new AiRobot();
        robot.setSystemPrompt("You are a test bot.");
        robot.setTemperature(0.5);
        robot.setOutputTokensLimit(256);
        return robot;
    }

    @Test
    void callSync_returnsTextAndUsage() {
        ChatResponse resp = response(new AssistantMessage("Hello world"),
                ChatResponseMetadata.builder().usage(new DefaultUsage(10, 5, 15)).build());
        ChatExecutor executor = executorFor(fakeModel(resp, List.of()));

        ChatExecutor.ChatResult result = executor.callSync(new AiModel(), robot(), List.of(), "hi");

        assertThat(result.text()).isEqualTo("Hello world");
        assertThat(result.usage().promptTokens()).isEqualTo(10);
        assertThat(result.usage().completionTokens()).isEqualTo(5);
        assertThat(result.usage().totalTokens()).isEqualTo(15);
    }

    @Test
    void stream_accumulatesText_escapesNewlines_sendsDoneAndUsage() throws Exception {
        List<ChatResponse> chunks = List.of(
                response(new AssistantMessage("Hello"), null),
                response(new AssistantMessage("\nworld"),
                        ChatResponseMetadata.builder().usage(new DefaultUsage(7, 3, 10)).build()));
        ChatExecutor executor = executorFor(fakeModel(null, chunks));

        SseEmitter emitter = mock(SseEmitter.class);
        AtomicReference<ChatExecutor.ChatResult> completed = new AtomicReference<>();
        CountDownLatch latch = new CountDownLatch(1);

        executor.stream(new AiModel(), robot(), List.of(), "hi", emitter, result -> {
            completed.set(result);
            latch.countDown();
        });

        // Streaming completes asynchronously (Reactor) — wait for the terminal callback
        assertThat(latch.await(5, TimeUnit.SECONDS)).as("stream completed").isTrue();

        // onComplete saw the full, un-rebatched text and the usage from the final chunk
        assertThat(completed.get()).isNotNull();
        assertThat(completed.get().text()).isEqualTo("Hello\nworld");
        assertThat(completed.get().usage().completionTokens()).isEqualTo(3);
        assertThat(completed.get().usage().promptTokens()).isEqualTo(7);

        // Capture everything sent on the wire
        ArgumentCaptor<Object> sent = ArgumentCaptor.forClass(Object.class);
        verify(emitter, atLeastOnce()).send(sent.capture());
        List<Object> messages = sent.getAllValues();

        // The [DONE] sentinel is sent
        assertThat(messages).contains(AiConstant.STREAM_END_MESSAGE);

        // No raw newline reaches the wire; un-escaping the content sends reproduces the full text
        StringBuilder rebuilt = new StringBuilder();
        for (Object msg : messages) {
            String s = String.valueOf(msg);
            if (AiConstant.STREAM_END_MESSAGE.equals(s)) {
                continue;
            }
            assertThat(s).doesNotContain("\n");
            rebuilt.append(s.replace((char) 0x1A, '\n'));
        }
        assertThat(rebuilt.toString()).isEqualTo("Hello\nworld");
    }
}
