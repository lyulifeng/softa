package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Deployment lifecycle status.
 * <p>
 * {@code PENDING} → {@code DEPLOYING} → {@code SUCCESS} / {@code FAILURE};
 * {@code PENDING} / {@code DEPLOYING} → {@code CANCELED} (operator action).
 * <p>
 * A {@code PENDING} deployment may park awaiting DBA approval
 * ({@code ddlApplyStatus = PENDING_DBA_APPROVAL}, ADR-0012) before any envelope
 * is dispatched; {@code confirmDdlApplied} resumes it to {@code DEPLOYING}.
 * <p>
 * There is deliberately no {@code ROLLED_BACK} state (roll-forward only,
 * ADR-0012): undoing an applied deployment means authoring a reverse change and
 * deploying it through the normal channel.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Design Release Deployment Status")
public enum DesignDeploymentStatus {
    PENDING("Pending"),
    DEPLOYING("Deploying"),
    SUCCESS("Success"),
    FAILURE("Failure"),
    CANCELED("Canceled"),
    ;

    @JsonValue
    private final String status;
}
