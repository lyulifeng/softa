package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Login Status
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum LoginStatus {
    SUCCESS("Success"),
    INVALID("Invalid"),
    NOT_FOUND("NotFound"),
    ;

    @JsonValue
    private final String status;
}
