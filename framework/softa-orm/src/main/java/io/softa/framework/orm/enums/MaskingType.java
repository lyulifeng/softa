package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Masking type:
 *      ALL: default, all characters are replaced with `****`,
 *      NAME: Retain the first and last characters.
 *          If the name contains only two characters, the last character is retained.
 *      EMAIL: Only retain the first 4 characters.
 *      PHONE_NUMBER: replace the last 4 digits with `****`,
 *      ID_NUMBER: Retain the first and last 4 digits and replace other characters with `****`,
 *      CARD_NUMBER: Only retain the last 4 characters.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum MaskingType {
    @OptionItem(label = "Masks All Content")
    ALL("All"),
    @OptionItem(label = "Masks Name")
    NAME("Name"),
    @OptionItem(label = "Masks Email")
    EMAIL("Email"),
    @OptionItem(label = "Masks Phone Number")
    PHONE_NUMBER("PhoneNumber"),
    @OptionItem(label = "Masks ID Number")
    ID_NUMBER("IdNumber"),
    @OptionItem(label = "Masks Card Number")
    CARD_NUMBER("CardNumber");

    @JsonValue
    private final String type;
}
