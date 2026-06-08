package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Login Status
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Login Status")
public enum LoginStatus {
    @OptionItem(label = "Success")
    SUCCESS("Success"),
    @OptionItem(label = "Invalid")
    INVALID("Invalid"),
    @OptionItem(label = "Not Found")
    NOT_FOUND("NotFound"),
    ;

    @JsonValue
    private final String status;
}
