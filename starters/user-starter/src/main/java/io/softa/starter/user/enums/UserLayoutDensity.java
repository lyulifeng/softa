package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * User Layout Density
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum UserLayoutDensity {
    DEFAULT("Default"),
    COMPACT("Compact"),
    COMFORTABLE("Comfortable");

    @JsonValue
    private final String value;
}
