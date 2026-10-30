package io.softa.starter.flow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * When a flow's CC rule copies its recipients in: as the approval is submitted, or as it ends approved or
 * rejected. A setting of the whole approval, not of a step.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum CcTiming {
    ON_SUBMIT("OnSubmit"),
    ON_APPROVED("OnApproved"),
    ON_REJECTED("OnRejected"),
    ;

    @JsonValue
    private final String type;
}
