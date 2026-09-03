package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Carries only the new password: this is the first-password path, so there is no current password
 * to confirm. The absence of that field is enforced server-side by refusing the call when one is
 * already set — see {@code UserAccountService.setMyFirstPassword}.
 */
@Data
public class SetFirstPasswordDTO {

    @NotBlank(message = "New password cannot be empty!")
    private String newPassword;
}
