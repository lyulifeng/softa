package io.softa.starter.ai.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * AI Stream Request Message
 */
@Data
@Schema(name = "AI Stream Request")
public class AiStreamRequest {

    @Schema(description = "User Message ID")
    @NotNull(message = "User Message ID cannot be empty!")
    private Long userMessageId;

    @Schema(description = "AI Message ID")
    @NotNull(message = "AI Message ID cannot be empty!")
    private Long aiMessageId;

}
