package io.softa.starter.user.dto;

import jakarta.validation.constraints.NotNull;

/** Request body for {@code PUT /Role/{id}/active} — enable / disable a role. */
public record RoleActiveDTO(@NotNull(message = "`active` (boolean) is required") Boolean active) {
}
