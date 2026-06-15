package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Tenant status enum.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum TenantStatus {
    DRAFT("Draft"),
    ACTIVE("Active"),
    SUSPENDED("Suspended"),
    CLOSED("Closed"),
    ;

    @JsonValue
    private final String status;

}
