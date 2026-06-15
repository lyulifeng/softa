package io.softa.starter.ai.model;

import java.io.IOException;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.ai.chat.client.ChatClient;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.metadata.Usage;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.ChatOptions;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import io.softa.framework.base.exception.IntegrationException;
import io.softa.starter.ai.constant.AiConstant;
import io.softa.starter.ai.dto.TokenUsage;
import io.softa.starter.ai.entity.AiModel;
import io.softa.starter.ai.entity.AiRobot;

/**
 * Owns all Spring AI interaction. The orchestration layer ({@code AiRobotServiceImpl})
 * never touches an SDK type — it hands a robot + history + user content here and gets a
 * {@link ChatResult} (SDK-agnostic) back, or streams via an {@link SseEmitter}.
 * <p>
 * Model identity + credentials come from the cached {@code ChatModel} (built per
 * {@link AiModel} row); generation params (temperature / maxTokens / penalties) are
 * applied per-call as portable {@link ChatOptions} merged over the model's defaults.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class ChatExecutor {

    /**
     * Replaces '\n' on the SSE wire so a newline cannot break the event framing
     * (the frontend unescapes it). 0x1A == ASCII SUB, matching the legacy protocol.
     */
    private static final char NEWLINE_PLACEHOLDER = (char) 0x1A;

    private final ChatModelRegistry chatModelRegistry;

    /** SDK-agnostic result of a synchronous (or fully-accumulated streaming) chat. */
    public record ChatResult(String text, TokenUsage usage) {
    }

    /**
     * Synchronous chat. Returns the assistant text and token usage.
     */
    public ChatResult callSync(AiModel aiModel, AiRobot robot, List<Message> history, String userContent) {
        ChatClient client = chatModelRegistry.getChatClient(aiModel);
        ChatResponse response = buildRequest(client, robot, history, userContent).call().chatResponse();
        String text = response != null && response.getResult() != null
                ? response.getResult().getOutput().getText() : "";
        Usage usage = response != null ? response.getMetadata().getUsage() : null;
        return new ChatResult(text, TokenUsage.from(usage));
    }

    /**
     * Streaming chat over SSE. Each delta is sent to the client (preserving the legacy
     * wire protocol: newlines escaped, a trailing {@code [DONE]} sentinel); on completion
     * the accumulated {@link ChatResult} is handed to {@code onComplete} for persistence.
     */
    public void stream(AiModel aiModel, AiRobot robot, List<Message> history, String userContent,
                       SseEmitter emitter, Consumer<ChatResult> onComplete) {
        ChatClient client = chatModelRegistry.getChatClient(aiModel);
        StringBuilder buffer = new StringBuilder();
        AtomicReference<Usage> usageRef = new AtomicReference<>();

        buildRequest(client, robot, history, userContent)
                .stream()
                .chatResponse()
                .subscribe(
                        chunk -> onChunk(chunk, buffer, usageRef, emitter),
                        error -> {
                            log.error("AI stream error", error);
                            emitter.completeWithError(error);
                        },
                        () -> {
                            sendStreamEnd(emitter);
                            onComplete.accept(new ChatResult(buffer.toString(), TokenUsage.from(usageRef.get())));
                        });
    }

    private void onChunk(ChatResponse chunk, StringBuilder buffer, AtomicReference<Usage> usageRef, SseEmitter emitter) {
        if (chunk == null) {
            return;
        }
        usageRef.set(chunk.getMetadata().getUsage());
        String delta = chunk.getResult() != null
                ? chunk.getResult().getOutput().getText() : null;
        if (StringUtils.isEmpty(delta)) {
            return;
        }
        buffer.append(delta);
        try {
            emitter.send(delta.replace('\n', NEWLINE_PLACEHOLDER));
        } catch (IOException e) {
            throw new IntegrationException(e.getMessage(), e);
        }
    }

    private void sendStreamEnd(SseEmitter emitter) {
        try {
            emitter.send(AiConstant.STREAM_END_MESSAGE);
        } catch (IOException e) {
            log.warn("Failed to send stream end message to client: {}", e.getMessage());
        }
    }

    private ChatClient.ChatClientRequestSpec buildRequest(ChatClient client, AiRobot robot,
                                                          List<Message> history, String userContent) {
        ChatClient.ChatClientRequestSpec spec = client.prompt();
        spec = spec.options(buildOptions(robot));
        if (StringUtils.isNotBlank(robot.getSystemPrompt())) {
            spec = spec.system(robot.getSystemPrompt());
        }
        if (history != null && !history.isEmpty()) {
            spec = spec.messages(history);
        }
        return spec.user(userContent);
    }

    private ChatOptions.Builder<?> buildOptions(AiRobot robot) {
        ChatOptions.Builder<?> options = ChatOptions.builder();
        if (robot.getTemperature() != null) {
            options.temperature(robot.getTemperature());
        }
        if (robot.getOutputTokensLimit() != null && robot.getOutputTokensLimit() > 0) {
            options.maxTokens(robot.getOutputTokensLimit());
        }
        if (robot.getPresencePenalty() != null) {
            options.presencePenalty(robot.getPresencePenalty());
        }
        if (robot.getFrequencyPenalty() != null) {
            options.frequencyPenalty(robot.getFrequencyPenalty());
        }
        return options;
    }
}
