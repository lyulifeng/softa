package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/inviteInfo} — the invitation / reset token to validate. */
@Data
public class InviteInfoDTO {
    @NotBlank(message = "Token cannot be empty!")
    private String token;
}
