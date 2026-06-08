package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Option Item Tone")
public enum OptionItemTone {
    @OptionItem(label = "Success")
    SUCCESS("Success"),
    @OptionItem(label = "Warning")
    WARNING("Warning"),
    @OptionItem(label = "Error")
    ERROR("Error"),
    @OptionItem(label = "Info")
    INFO("Info"),
    @OptionItem(label = "Neutral")
    NEUTRAL("Neutral")
    ;

    @JsonValue
    private final String code;
}
