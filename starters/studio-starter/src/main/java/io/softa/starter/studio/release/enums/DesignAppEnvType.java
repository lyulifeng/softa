package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Design App Env Type")
public enum DesignAppEnvType {
    @OptionItem(label = "Dev")
    DEV("Dev"),
    @OptionItem(label = "Test")
    TEST("Test"),
    @OptionItem(label = "UAT")
    UAT("UAT"),
    @OptionItem(label = "Prod")
    PROD("Prod");

    @JsonValue
    private final String type;
}
