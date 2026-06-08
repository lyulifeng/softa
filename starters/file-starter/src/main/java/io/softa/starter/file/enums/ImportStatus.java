package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Import Status Enum
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Import Status")
public enum ImportStatus {
    @OptionItem(label = "Processing")
    PROCESSING("Processing"),
    @OptionItem(label = "Success")
    SUCCESS("Success"),
    @OptionItem(label = "Failure")
    FAILURE("Failure"),
    @OptionItem(label = "Partial Failure")
    PARTIAL_FAILURE("PartialFailure"),
    @OptionItem(label = "Validation Success")
    VALIDATION_SUCCESS("ValidationSuccess"),
    @OptionItem(label = "Validation Failure")
    VALIDATION_FAILURE("ValidationFailure"),
    ;

    @JsonValue
    private final String code;
}
