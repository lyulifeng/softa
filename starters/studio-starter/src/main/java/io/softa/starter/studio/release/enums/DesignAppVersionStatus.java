package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design App Version Status")
public enum DesignAppVersionStatus {
    @OptionItem(label = "Draft")
    DRAFT("Draft"),
    @OptionItem(label = "Sealed")
    SEALED("Sealed"),
    @OptionItem(label = "Frozen")
    FROZEN("Frozen"),
    ;

    @JsonValue
    private final String status;
}
