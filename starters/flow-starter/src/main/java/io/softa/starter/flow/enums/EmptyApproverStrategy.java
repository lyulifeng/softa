package io.softa.starter.flow.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Strategy applied when an approval node resolves to zero eligible approvers.
 * Configurable per-node via {@code config.emptyApproverStrategy}.
 */
@Getter
@AllArgsConstructor
public enum EmptyApproverStrategy {
    /** Throw an error and halt the flow (default). */
    ERROR("Error"),
    /** Skip the approval node and continue execution. */
    SKIP("Skip"),
    /** Auto-approve the node and continue execution. */
    AUTO_APPROVE("AutoApprove"),
    ;

    @JsonValue
    private final String type;
}

