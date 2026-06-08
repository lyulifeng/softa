package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design App Version Type")
public enum DesignAppVersionType {
    @OptionItem(label = "Normal", description = "Normal planned release")
    NORMAL("Normal"),
    @OptionItem(label = "Hotfix", description = "Emergency hotfix release")
    HOTFIX("Hotfix"),
    ;

    @JsonValue
    private final String type;
}
