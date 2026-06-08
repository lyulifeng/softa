package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Tenant status enum.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Tenant Status")
public enum TenantStatus {
    @OptionItem(label = "Draft")
    DRAFT("Draft"),
    @OptionItem(label = "Active")
    ACTIVE("Active"),
    @OptionItem(label = "Suspended")
    SUSPENDED("Suspended"),
    @OptionItem(label = "Closed")
    CLOSED("Closed"),
    ;

    @JsonValue
    private final String status;

}
