package io.softa.starter.cron.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Cron status
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Cron Status")
public enum CronStatus {
    @OptionItem(label = "Scheduled", description = "Scheduled for a specified time.")
    Scheduled("Scheduled"),
    @OptionItem(label = "Running", description = "Currently executing.")
    RUNNING("Running"),
    @OptionItem(label = "Completed", description = "Finished successfully.")
    COMPLETED("Completed"),
    @OptionItem(label = "Paused", description = "Temporarily paused.")
    PAUSED("Paused"),
    @OptionItem(label = "Cancelled", description = "Cancelled before completion.")
    CANCELLED("Cancelled"),
    @OptionItem(label = "Skipped", description = "Skipped due to unmet execution conditions.")
    SKIPPED("Skipped"),
    @OptionItem(label = "Timeout", description = "Interrupted by execution timeout.")
    TIMEOUT("Timeout"),
    @OptionItem(label = "Failed", description = "Execution failed.")
    FAILED("Failed");

    @JsonValue
    private final String status;
}
