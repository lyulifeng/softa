package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tenant Status
 */
@Getter
@AllArgsConstructor
public enum TenantStatus {
    DRAFT("Draft", "Created, but not yet fully activated; disallowed for normal business operations"),
    ACTIVE("Active", "Normal operation, accessible for login and business execution"),
    SUSPENDED("Suspended", "Temporary suspension; data retained but access or critical operations restricted"),
    CLOSED("Closed", "Permanent closure; no longer providing services"),
    ;

    @JsonValue
    private final String status;
    private final String description;
}
