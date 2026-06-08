package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * OAuth Provider
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "OAuth Provider")
public enum OAuthProvider {
    @OptionItem(label = "Apple")
    APPLE("Apple"),
    @OptionItem(label = "Google")
    GOOGLE("Google"),
    @OptionItem(label = "TikTok")
    TIKTOK("TikTok"),
    @OptionItem(label = "X")
    X("X"),
    @OptionItem(label = "LinkedIn")
    LINKED_IN("LinkedIn");

    @JsonValue
    private final String provider;
}
