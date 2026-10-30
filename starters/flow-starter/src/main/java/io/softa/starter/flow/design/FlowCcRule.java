package io.softa.starter.flow.design;

import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import io.softa.starter.flow.enums.CcTiming;

/**
 * An approval flow's CC rule: at its timing — the approval submitted, approved or rejected — its recipients get a
 * read-only copy of the approval, in their CC list and inbox. Recipients are found the way an approval step's
 * approvers are: named users ({@code recipients}), or a source ({@code recipientSource}, an
 * {@code approverSource}'s shape — the subject's supervisor, a role, a department's members…), never both.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(name = "FlowCcRule")
public class FlowCcRule {

    @Schema(description = "When: OnSubmit, OnApproved or OnRejected")
    private CcTiming timing;

    @Schema(description = "Named recipients: user ids. Exclusive with recipientSource")
    private List<String> recipients;

    @Schema(description = "Recipients found at the time, in an approval step's approverSource shape "
            + "({\"type\": \"Supervisor|RoleQuery|Position|Department|…\", …}). Exclusive with recipients")
    private Map<String, Object> recipientSource;
}
