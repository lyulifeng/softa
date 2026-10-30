package io.softa.starter.flow.runtime.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.softa.starter.flow.enums.ApprovalTimeoutStrategy;

/**
 * Timeout configuration for an approval node: the step's deadline and what happens at it.
 * Extracted from {@code CompiledFlowNode.config} when present. Without {@code timeoutHours} the step has no
 * deadline and the rest is ignored.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "ApprovalTimeoutConfig")
public class ApprovalTimeoutConfig {

    @Schema(description = "Hours the step may wait before it is past its deadline; unset = no deadline, and the "
            + "rest of the timeout is ignored")
    private Integer timeoutHours;

    @Schema(description = "What happens at the deadline; unset = Remind")
    private ApprovalTimeoutStrategy timeoutStrategy;

    @Schema(description = "Remind only: hours between reminders, the first sent at the deadline; unset = one "
            + "reminder, at the deadline")
    private Integer remindIntervalHours;

    @Schema(description = "Remind only, with an interval: the most reminders sent, the one at the deadline "
            + "included; unset = 3")
    private Integer maxRemindTimes;

    @Schema(description = "Escalation target user ID when strategy is ESCALATE")
    private String escalateToUserId;
}

