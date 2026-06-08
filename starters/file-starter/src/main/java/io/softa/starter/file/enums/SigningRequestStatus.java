package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Signing request status.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Signing Request Status")
public enum SigningRequestStatus {
    @OptionItem(label = "Draft")
    DRAFT("Draft"),
    @OptionItem(label = "Sent")
    SENT("Sent"),
    @OptionItem(label = "In Progress")
    IN_PROGRESS("InProgress"),
    @OptionItem(label = "Completed")
    COMPLETED("Completed"),
    @OptionItem(label = "Cancelled")
    CANCELLED("Cancelled"),
    @OptionItem(label = "Expired")
    EXPIRED("Expired"),
    ;

    @JsonValue
    private final String status;

}
