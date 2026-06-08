package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * User Layout Density
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "User Layout Density")
public enum UserLayoutDensity {
    @OptionItem(label = "Default")
    DEFAULT("Default"),
    @OptionItem(label = "Compact")
    COMPACT("Compact"),
    @OptionItem(label = "Comfortable")
    COMFORTABLE("Comfortable");

    @JsonValue
    private final String value;
}
