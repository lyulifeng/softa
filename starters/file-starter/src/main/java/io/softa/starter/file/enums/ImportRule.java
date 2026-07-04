package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Import Rule Enum
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum ImportRule {
    CREATE_OR_UPDATE("CreateOrUpdate"),
    ONLY_UPDATE("OnlyUpdate"),
    ONLY_CREATE("OnlyCreate");

    @JsonValue
    private final String code;
}
