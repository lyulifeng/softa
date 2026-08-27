package io.softa.starter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * The corrected work contacts for Reset User (W9).
 *
 * <p>Neither is individually required — an employee reachable only by work mobile is ordinary — but
 * at least one must be present, which the service enforces because "one of these two" is not a
 * per-field constraint.
 */
@Data
@Schema(description = "Reset a membership's work contacts, keeping the person")
public class ResetWorkContactsDTO {

    @Schema(description = "New work email")
    private String email;

    @Schema(description = "New work mobile, dial code included")
    private String mobile;

    @Schema(description = "Reason, recorded for auditing")
    private String reason;
}
