package io.softa.starter.file.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum DocumentTemplateType {
    PDF("PDF", "Upload a PDF template"),
    WORD("Word", "Upload a Word template"),
    RICH_TEXT("RichText", "Online Rich Text Editor"),;

    @JsonValue
    private final String type;
    private final String description;
}
