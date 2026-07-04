package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * Document template type.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum DocumentTemplateType {
    @OptionItem(description = "Online Rich Text Editor")
    RICH_TEXT("RichText"),
    @OptionItem(description = "Upload a Word template")
    WORD("Word"),
    @OptionItem(description = "Upload a PDF template")
    PDF("PDF"),
    ;

    @JsonValue
    private final String type;
}
