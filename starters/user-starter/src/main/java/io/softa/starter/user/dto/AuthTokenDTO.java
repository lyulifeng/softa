package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/listTenants} — the pre-auth token minted by authentication. */
@Data
public class AuthTokenDTO {

    @NotBlank(message = "Auth token cannot be empty!")
    private String authToken;
}
