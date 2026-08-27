package io.softa.starter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * Freeze / unfreeze request payload — the reason is recorded for auditing.
 */
@Data
@Schema(description = "Freeze or unfreeze a user account")
public class FreezeAccountDTO {

    @Schema(description = "Reason, recorded for auditing")
    private String reason;
}

