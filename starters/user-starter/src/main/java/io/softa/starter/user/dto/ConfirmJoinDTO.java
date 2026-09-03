package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request body for {@code POST /login/confirmJoin}. */
@Data
public class ConfirmJoinDTO {

    @NotBlank(message = "Token cannot be empty!")
    private String token;

    @NotNull(message = "Profile id cannot be empty!")
    private Long profileId;

    /** Returned by verifyJoinCode; ties this anonymous call to a code that was actually passed. */
    @NotBlank(message = "Proof cannot be empty!")
    private String proof;
}
