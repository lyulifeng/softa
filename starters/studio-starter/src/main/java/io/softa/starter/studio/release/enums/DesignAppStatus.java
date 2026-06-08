package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design App Status")
public enum DesignAppStatus {
    @OptionItem(label = "Active")
    ACTIVE("Active"),
    @OptionItem(label = "Maintenance")
    MAINTENANCE("Maintenance"),
    @OptionItem(label = "Deprecated")
    DEPRECATED("Deprecated"),
    ;

    @JsonValue
    private final String status;
}
