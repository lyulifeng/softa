package io.softa.starter.ai.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * AI starter configuration (non-secret defaults only — credentials live on the
 * {@code AiModel} row, encrypted at rest). Bound from the {@code softa.ai} prefix.
 */
@Data
@ConfigurationProperties(prefix = "softa.ai")
public class AiProperties {

    /** Max number of prior conversation messages replayed to the model as history. */
    private int historyWindowSize = 20;

    /** SSE emitter timeout in milliseconds for streaming chat. */
    private long responseTimeout = 60000L;
}
