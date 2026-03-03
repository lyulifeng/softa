package io.softa.starter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Unlock account request payload.
 */
@Data
@Schema(description = "Unlock user account request")
public class UnlockAccountDTO {

    @NotNull
    @Schema(description = "User ID to unlock", requiredMode = Schema.RequiredMode.REQUIRED)
    private Long id;

    @Schema(description = "Unlock reason")
    private String reason;
}

