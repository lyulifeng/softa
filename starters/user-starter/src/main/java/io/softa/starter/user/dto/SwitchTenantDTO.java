package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for {@code POST /login/switchTenant} — the company to move the session to.
 *
 * <p>No token, unlike {@link SelectTenantDTO}: the caller is already signed in, so the current
 * session names the person. Carrying a pre-auth token here would add a second, weaker way to reach
 * the same session-minting step.
 */
@Data
public class SwitchTenantDTO {

    @NotNull(message = "Account id cannot be empty!")
    private Long accountId;
}
