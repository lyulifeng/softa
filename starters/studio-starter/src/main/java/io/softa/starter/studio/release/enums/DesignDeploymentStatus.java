package io.softa.starter.studio.release.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Deployment lifecycle status.
 * <p>
 * {@code PENDING} → {@code DEPLOYING} → {@code SUCCESS} / {@code FAILURE} / {@code ROLLED_BACK}
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Design Release Deployment Status")
public enum DesignDeploymentStatus {
    @OptionItem(label = "Pending")
    PENDING("Pending"),
    @OptionItem(label = "Deploying")
    DEPLOYING("Deploying"),
    @OptionItem(label = "Success")
    SUCCESS("Success"),
    @OptionItem(label = "Failure")
    FAILURE("Failure"),
    @OptionItem(label = "Canceled")
    CANCELED("Canceled"),
    @OptionItem(label = "Rolled Back")
    Rolled_Back("Rolled Back"),
    ;

    @JsonValue
    private final String status;
}
