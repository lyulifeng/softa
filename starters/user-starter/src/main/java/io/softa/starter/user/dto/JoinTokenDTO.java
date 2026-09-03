package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/joinEntry} — the invitation token from the /join link. */
@Data
public class JoinTokenDTO {

    @NotBlank(message = "Token cannot be empty!")
    private String token;
}
