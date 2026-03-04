package io.softa.framework.orm.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Data access type
 */
@AllArgsConstructor
@Getter
public enum AccessType {
    READ,
    UPDATE,
    CREATE,
    DELETE
}
