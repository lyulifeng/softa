package io.softa.starter.user.dto;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Bulk freeze / unfreeze request payload.
 */
@Data
@Schema(description = "Freeze or unfreeze user accounts")
public class FreezeAccountsDTO {

    @NotNull
    @Schema(description = "User IDs", requiredMode = Schema.RequiredMode.REQUIRED)
    private List<Long> ids;

    @Schema(description = "Reason, recorded for auditing")
    private String reason;
}

