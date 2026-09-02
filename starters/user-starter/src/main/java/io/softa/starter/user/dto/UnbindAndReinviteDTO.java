package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The mandatory reason for Unbind & Re-invite (W5) — and nothing else.
 *
 * <p>It carries no contacts: the corrected contact is HR's edit to the EMPLOYEE RECORD, which is
 * the single source of those (S-B / D23), and this operation reads it from there. A typo is fixed
 * where it lives rather than retyped here, so the two cannot end up disagreeing.
 *
 * <p>The reason is required at THIS layer as well as in the service: a bean-validation failure
 * names the field for the form, which a service exception cannot.
 */
@Data
public class UnbindAndReinviteDTO {

    @NotBlank(message = "A reason is required to unbind this account!")
    @Size(max = 500, message = "The reason cannot exceed 500 characters!")
    private String reason;
}
