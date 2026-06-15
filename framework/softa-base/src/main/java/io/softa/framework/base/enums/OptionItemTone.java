package io.softa.framework.base.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum OptionItemTone {
    SUCCESS("Success"),
    WARNING("Warning"),
    ERROR("Error"),
    INFO("Info"),
    NEUTRAL("Neutral")
    ;

    @JsonValue
    private final String code;
}
