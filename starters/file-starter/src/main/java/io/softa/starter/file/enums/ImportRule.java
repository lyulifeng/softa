package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Import Rule Enum
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Import Rule")
public enum ImportRule {
    @OptionItem(label = "Create or Update")
    CREATE_OR_UPDATE("CreateOrUpdate"),
    @OptionItem(label = "Only Update")
    ONLY_UPDATE("OnlyUpdate"),
    @OptionItem(label = "Only Create")
    ONLY_CREATE("OnlyCreate");

    @JsonValue
    private final String code;
}
