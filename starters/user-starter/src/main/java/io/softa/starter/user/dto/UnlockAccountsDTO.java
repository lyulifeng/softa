package io.softa.starter.user.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Unlock accounts request payload.
 */
@Data
@Schema(description = "Unlock user accounts request")
public class UnlockAccountsDTO {

    @NotNull
    @Schema(description = "User IDs to unlock", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "Unlock reason")
    private String reason;
}

