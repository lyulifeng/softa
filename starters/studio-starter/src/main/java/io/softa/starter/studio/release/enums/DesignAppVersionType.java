package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet
public enum DesignAppVersionType {
    @OptionItem(description = "Normal planned release")
    NORMAL("Normal"),
    @OptionItem(description = "Emergency hotfix release")
    HOTFIX("Hotfix"),
    ;

    @JsonValue
    private final String type;
}
