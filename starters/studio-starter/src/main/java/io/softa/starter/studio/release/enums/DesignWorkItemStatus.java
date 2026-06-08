package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design Work Item Status")
public enum DesignWorkItemStatus {
    @OptionItem(label = "In Progress")
    IN_PROGRESS("InProgress"),
    @OptionItem(label = "Done")
    DONE("Done"),
    @OptionItem(label = "Deferred")
    DEFERRED("Deferred"),
    @OptionItem(label = "Closed")
    CLOSED("Closed"),
    @OptionItem(label = "Cancelled")
    CANCELLED("Cancelled"),
    ;

    @JsonValue
    private final String status;
}
