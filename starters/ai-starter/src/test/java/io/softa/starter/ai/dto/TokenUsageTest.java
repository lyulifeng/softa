package io.softa.starter.ai.dto;

import org.junit.jupiter.api.Test;
import org.springframework.ai.chat.metadata.DefaultUsage;
import org.springframework.ai.chat.metadata.Usage;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for {@link TokenUsage} — the SDK-agnostic usage boundary type.
 * {@code from()} treats a null {@link Usage} as empty; an individual null token
 * count (Spring AI's getters all return nullable {@link Integer}) degrades to 0.
 */
class TokenUsageTest {

    /** A {@link Usage} that reports the given counts verbatim, including nulls. */
    private static Usage usage(Integer prompt, Integer completion, Integer total) {
        return new Usage() {
            @Override
            public Integer getPromptTokens() {
                return prompt;
            }

            @Override
            public Integer getCompletionTokens() {
                return completion;
            }

            @Override
            public Integer getTotalTokens() {
                return total;
            }

            @Override
            public Object getNativeUsage() {
                return null;
            }
        };
    }

    @Test
    void empty_isAllZero() {
        assertThat(TokenUsage.empty()).isEqualTo(new TokenUsage(0, 0, 0));
    }

    @Test
    void from_null_isEmpty() {
        assertThat(TokenUsage.from(null)).isEqualTo(TokenUsage.empty());
    }

    @Test
    void from_fullUsage_mapsAllFields() {
        Usage usage = new DefaultUsage(10, 5, 15);
        assertThat(TokenUsage.from(usage)).isEqualTo(new TokenUsage(10, 5, 15));
    }

    @Test
    void from_allNullCounts_degradesToZero() {
        assertThat(TokenUsage.from(usage(null, null, null))).isEqualTo(TokenUsage.empty());
    }

    @Test
    void from_partialNullCounts_degradesPerField() {
        assertThat(TokenUsage.from(usage(10, null, null))).isEqualTo(new TokenUsage(10, 0, 0));
    }
}
