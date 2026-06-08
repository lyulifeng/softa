package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tenant lifecycle stage enum.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Tenant Lifecycle")
public enum TenantLifecycle {
    @OptionItem(label = "Trial")
    TRIAL("Trial"),
    @OptionItem(label = "Subscribed")
    SUBSCRIBED("Subscribed"),
    @OptionItem(label = "Grace Period")
    GRACE_PERIOD("GracePeriod"),
    @OptionItem(label = "Offboarding")
    OFFBOARDING("Offboarding"),
    @OptionItem(label = "Archived")
    ARCHIVED("Archived"),
    ;

    @JsonValue
    private final String stage;

}
