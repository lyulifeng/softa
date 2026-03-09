package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tenant Lifecycle Stage
 */
@Getter
@AllArgsConstructor
public enum TenantLifecycleStage {

    TRIAL("TRIAL", "The tenant is in a trial period with temporary access to the service before a paid subscription begins."),
    SUBSCRIBED("SUBSCRIBED", "The tenant has an active paid subscription and is in normal business operation. "),
    GRACE_PERIOD("GRACE_PERIOD", "The tenant's subscription has expired or is pending renewal, but limited service access is still temporarily allowed."),
    OFFBOARDING("OFFBOARDING", "The tenant is in the process of service termination, data export, migration, or administrative closure."),
    ARCHIVED("ARCHIVED", "The tenant is no longer active and is retained only for historical reference, audit, or compliance purposes.");

    @JsonValue
    private final String stage;

    private final String description;
}
