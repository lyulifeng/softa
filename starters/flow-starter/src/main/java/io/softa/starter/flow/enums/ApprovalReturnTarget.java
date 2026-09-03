package io.softa.starter.flow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Supported targets for returning a pending approval.
 */
@Getter
@AllArgsConstructor
public enum ApprovalReturnTarget {
    INITIATOR("Initiator", "Initiator"),
    PREVIOUS_APPROVAL("PreviousApproval", "Previous Approval"),
    SPECIFIC_NODE("SpecificNode", "Specific Node"),
    ;

    @JsonValue
    private final String type;
    private final String name;

}

