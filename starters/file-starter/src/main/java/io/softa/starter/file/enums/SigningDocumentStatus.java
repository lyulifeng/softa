package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Signing document status.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Signing Document Status")
public enum SigningDocumentStatus {
    @OptionItem(label = "Pending")
    PENDING("Pending"),
    @OptionItem(label = "In Progress")
    IN_PROGRESS("InProgress"),
    @OptionItem(label = "Completed")
    COMPLETED("Completed"),
    ;

    @JsonValue
    private final String status;

}
