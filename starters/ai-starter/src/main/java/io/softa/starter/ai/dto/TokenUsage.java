package io.softa.starter.ai.dto;

import org.springframework.ai.chat.metadata.Usage;

/**
 * SDK-agnostic token usage value object.
 * <p>
 * The service/DTO layer must never depend on a model SDK's usage type directly
 * (the previous design leaked the vendor SDK's {@code Usage} through service
 * signatures). This record is the single boundary type; Spring AI's
 * {@link Usage} is mapped into it at the model-layer edge via {@link #from(Usage)}.
 */
public record TokenUsage(int promptTokens, int completionTokens, int totalTokens) {

    public static TokenUsage empty() {
        return new TokenUsage(0, 0, 0);
    }

    /**
     * Null-safe mapping from Spring AI's {@link Usage}. Any null token count
     * (provider did not report it) degrades to {@code 0} — Spring AI's token
     * getters all return a nullable {@link Integer}.
     */
    public static TokenUsage from(Usage usage) {
        if (usage == null) {
            return empty();
        }
        return new TokenUsage(
                zeroIfNull(usage.getPromptTokens()),
                zeroIfNull(usage.getCompletionTokens()),
                zeroIfNull(usage.getTotalTokens()));
    }

    private static int zeroIfNull(Integer value) {
        return value == null ? 0 : value;
    }
}
