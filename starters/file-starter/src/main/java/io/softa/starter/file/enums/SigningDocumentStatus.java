package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Signing document status.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum SigningDocumentStatus {
    PENDING("Pending"),
    IN_PROGRESS("InProgress"),
    COMPLETED("Completed"),
    ;

    @JsonValue
    private final String status;

}
