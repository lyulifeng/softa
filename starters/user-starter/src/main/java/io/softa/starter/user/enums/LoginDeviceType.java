package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * WebBrowser, MobileApp, DesktopAPP, MiniProgram
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum LoginDeviceType {
    WEB_BROWSER("WebBrowser"),
    MOBILE_APP("MobileApp"),
    @OptionItem(label = "Desktop APP")
    DESKTOP_APP("DesktopAPP"),
    MINI_PROGRAM("MiniProgram"),
    ;

    @JsonValue
    private final String type;
}
