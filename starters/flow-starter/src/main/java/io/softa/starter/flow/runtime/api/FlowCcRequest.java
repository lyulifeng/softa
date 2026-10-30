package io.softa.starter.flow.runtime.api;

import java.util.List;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * Request for CC'ing people on a pending approval step: each gets a read-only copy of the approval.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Schema(name = "FlowCcRequest")
public class FlowCcRequest extends AbstractFlowNodeRequest {

    @NotEmpty(message = "targetActorIds must not be empty")
    @Schema(description = "The people to CC: user ids, one or more. Approvers of the step and people already CC'd "
            + "on it are skipped")
    private List<String> targetActorIds;
}
