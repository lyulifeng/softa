package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum DesignWorkItemStatus {
    IN_PROGRESS("InProgress"),
    DONE("Done"),
    DEFERRED("Deferred"),
    CLOSED("Closed"),
    CANCELLED("Cancelled"),
    ;

    @JsonValue
    private final String status;
}
