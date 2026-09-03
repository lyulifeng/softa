package io.softa.starter.user.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The audit reason for Reset User (W9) — and nothing else.
 *
 * <p>It carries no contacts: the employee record is the single source of those (S-B / D23), so the
 * service reads them from there. What a client posts cannot decide where somebody's sign-in lands,
 * and the account keeps holding the value being replaced right up to the write — which is what the
 * notification to the old address needs.
 */
@Data
@Schema(description = "Reset a membership's work contacts, keeping the person")
public class ResetWorkContactsDTO {

    @Schema(description = "Reason, recorded for auditing")
    @Size(max = 500, message = "The reason cannot exceed 500 characters!")
    private String reason;
}
