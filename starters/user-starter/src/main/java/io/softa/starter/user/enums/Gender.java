package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Male, Female
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum Gender {
    MALE("Male"),
    FEMALE("Female"),
    OTHER("Other");

    @JsonValue
    private final String gender;
}
