package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for {@code POST /login/loginByPassword}.
 *
 * <p>The identifier is whatever the person signs in with — a login email or a login mobile — so
 * it carries no format constraint: an {@code @Email} check here silently refused every password
 * login made from the Mobile tab, because "+6591234567" is not an email address. Which contact
 * the value names is decided by the identity lookup, not by the shape of the string.
 */
@Data
public class IdentifierPasswordDTO {

    @NotBlank(message = "Email or mobile cannot be empty!")
    private String identifier;

    @NotBlank(message = "Password cannot be empty!")
    private String password;

    private String tenantCode;
}
