package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /login/setJoinPassword} — the first password, set mid-join where no
 * session exists yet. The token plus the proof from verifyJoinCode, not the profile id, is what
 * authorizes the write.
 */
@Data
public class SetJoinPasswordDTO {

    @NotBlank(message = "Token cannot be empty!")
    private String token;

    @NotNull(message = "Profile id cannot be empty!")
    private Long profileId;

    @NotBlank(message = "New password cannot be empty!")
    private String newPassword;

    @NotBlank(message = "Proof cannot be empty!")
    private String proof;
}
