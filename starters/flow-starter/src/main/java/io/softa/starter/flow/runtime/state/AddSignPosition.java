package io.softa.starter.flow.runtime.state;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Position of an add-sign action relative to the source approver. Carries
 * {@code @OptionSet} because {@code FlowApprovalRecord.addSignPosition} is an OPTION field.
 */
@Getter
@AllArgsConstructor
// Explicit label: humanize would give "Add Sign Position", and the domain term the
// entity field already uses is hyphenated.
@OptionSet(label = "Add-Sign Position")
public enum AddSignPosition {
    BEFORE("Before"),
    AFTER("After"),
    ;

    @JsonValue
    private final String type;
}
