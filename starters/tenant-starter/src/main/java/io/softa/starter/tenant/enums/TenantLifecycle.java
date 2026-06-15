package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Tenant lifecycle stage enum.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum TenantLifecycle {
    TRIAL("Trial"),
    SUBSCRIBED("Subscribed"),
    GRACE_PERIOD("GracePeriod"),
    OFFBOARDING("Offboarding"),
    ARCHIVED("Archived"),
    ;

    @JsonValue
    private final String stage;

}
