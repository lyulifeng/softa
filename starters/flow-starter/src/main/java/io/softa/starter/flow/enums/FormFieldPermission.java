package io.softa.starter.flow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Field-level permission for approval node forms.
 */
@Getter
@AllArgsConstructor
public enum FormFieldPermission {
    HIDDEN("Hidden"),
    READONLY("Readonly"),
    EDITABLE("Editable"),
    REQUIRED("Required"),
    ;

    @JsonValue
    private final String type;
}

