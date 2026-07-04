package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Signing request status.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum SigningRequestStatus {
    DRAFT("Draft"),
    SENT("Sent"),
    IN_PROGRESS("InProgress"),
    COMPLETED("Completed"),
    CANCELLED("Cancelled"),
    EXPIRED("Expired"),
    ;

    @JsonValue
    private final String status;

}
