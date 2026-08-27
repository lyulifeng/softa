package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * The corrected work contacts plus the mandatory reason (W5).
 *
 * <p>Neither contact is individually required — an employee reachable only by work mobile is an
 * ordinary case — but at least one must be present, which the service enforces because "one of
 * these two" is not a per-field constraint.
 *
 * <p>The reason is required at THIS layer as well as in the service: a bean-validation failure
 * names the field for the form, which a service exception cannot.
 */
@Data
public class UnbindAndReinviteDTO {

    private String email;

    private String mobile;

    @NotBlank(message = "A reason is required to unbind this account!")
    @Size(max = 500, message = "The reason cannot exceed 500 characters!")
    private String reason;
}
