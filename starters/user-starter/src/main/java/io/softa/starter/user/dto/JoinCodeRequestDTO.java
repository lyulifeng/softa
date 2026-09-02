package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/** Request body for {@code POST /login/sendJoinCode} — which of the invitation's channels to send to. */
@Data
public class JoinCodeRequestDTO {

    @NotBlank(message = "Token cannot be empty!")
    private String token;

    /** {@code email} or {@code mobile}; resolved to an address by the invitation service. */
    @NotBlank(message = "Channel cannot be empty!")
    private String channel;
}
