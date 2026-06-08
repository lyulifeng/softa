package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Login Method
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Login Method")
public enum LoginMethod {
    @OptionItem(label = "Apple")
    APPLE_ID("Apple"),
    @OptionItem(label = "Google")
    GOOGLE("Google"),
    @OptionItem(label = "TikTok")
    TIKTOK("TikTok"),
    @OptionItem(label = "X")
    X("X"),
    @OptionItem(label = "Facebook")
    FACEBOOK("Facebook"),
    @OptionItem(label = "LinkedIn")
    LINKEDIN("LinkedIn"),
    @OptionItem(label = "Microsoft")
    MICROSOFT("Microsoft"),
    @OptionItem(label = "GitHub")
    GITHUB("GitHub"),
    @OptionItem(label = "Password")
    PASSWORD("Password"),
    @OptionItem(label = "SMS Code")
    SMS_CODE("SmsCode"),
    @OptionItem(label = "Email Code")
    EMAIL_CODE("EmailCode"),
    @OptionItem(label = "SSO")
    SSO("SSO"),
    @OptionItem(label = "WeChat")
    WE_CHAT("WeChat"),
    @OptionItem(label = "Alipay")
    ALIPAY("Alipay"),
    ;

    @JsonValue
    private final String method;
}
