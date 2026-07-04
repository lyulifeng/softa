package io.softa.framework.orm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Data access type
 */
@AllArgsConstructor
@Getter
@OptionSet
public enum AccessType {
    READ,
    UPDATE,
    CREATE,
    DELETE
}
