package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
@OptionSet(label = "Option Item Icon")
public enum OptionItemIcon {
    @OptionItem(label = "Check")
    CHECK("Check"),
    @OptionItem(label = "X")
    X("X"),
    @OptionItem(label = "Ban")
    BAN("Ban"),
    @OptionItem(label = "Alert")
    ALERT("Alert"),
    @OptionItem(label = "Pause")
    PAUSE("Pause"),
    @OptionItem(label = "Info")
    INFO("Info"),
    @OptionItem(label = "Eye")
    EYE("Eye"),
    @OptionItem(label = "Loader")
    LOADER("Loader"),
    @OptionItem(label = "Clock")
    CLOCK("Clock"),
    @OptionItem(label = "Pending")
    PENDING("Pending"),
    @OptionItem(label = "Undo")
    UNDO("Undo"),
    @OptionItem(label = "Lock")
    LOCK("Lock")
    ;

    @JsonValue
    private final String code;
}
