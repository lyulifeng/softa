package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Foreign-key delete strategy for a TO_ONE relation ({@code MANY_TO_ONE} / {@code ONE_TO_ONE}):
 * what happens to the referencing rows when the referenced ("One") row is deleted.
 *
 * <p>Unset ({@code @Field.onDelete} empty array / {@code sys_field.on_delete} NULL) = <b>KEEP</b>:
 * the framework does nothing, the FK is left as-is (the default). Enforcement is application-level
 * in {@code ModelServiceImpl.deleteByIds}; no physical DB {@code FOREIGN KEY ... ON DELETE} is ever
 * emitted (relations are app-level).
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "On Delete")
public enum OnDelete {

    /** Block the delete if any live ({@code deleted=false}) referencing row exists. */
    RESTRICT("Restrict"),

    /** Delete the referencing rows in the same transaction (each follows its own soft/hard delete). */
    CASCADE("Cascade"),

    /** Null out the referencing FK column — only when the referenced row is hard-deleted (no-op on soft delete). */
    SET_NULL("SetNull");

    @JsonValue
    private final String code;
}
