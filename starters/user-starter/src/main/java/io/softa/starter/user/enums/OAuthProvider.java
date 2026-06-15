package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * OAuth Provider
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "OAuth Provider")
public enum OAuthProvider {
    APPLE("Apple"),
    GOOGLE("Google"),
    @OptionItem(label = "TikTok")
    TIKTOK("TikTok"),
    X("X"),
    @OptionItem(label = "LinkedIn")
    LINKED_IN("LinkedIn");

    @JsonValue
    private final String provider;
}
