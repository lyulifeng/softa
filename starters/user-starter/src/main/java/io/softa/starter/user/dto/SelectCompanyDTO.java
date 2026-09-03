package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/** Request body for {@code POST /login/selectCompany} — the pre-auth token and the chosen membership. */
@Data
public class SelectCompanyDTO {

    @NotBlank(message = "Auth token cannot be empty!")
    private String authToken;

    @NotNull(message = "Account id cannot be empty!")
    private Long accountId;
}
