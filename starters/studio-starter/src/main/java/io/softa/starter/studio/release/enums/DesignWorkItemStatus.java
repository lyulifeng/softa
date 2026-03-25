package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DesignWorkItemStatus {
    IN_PROGRESS("InProgress", "In Progress"),
    READY("Ready", "Ready"),
    DEFERRED("Deferred", "Deferred"),
    DONE("Done", "Done"),
    CANCELLED("Cancelled", "Cancelled"),
    ;

    @JsonValue
    private final String status;

    private final String description;
}
