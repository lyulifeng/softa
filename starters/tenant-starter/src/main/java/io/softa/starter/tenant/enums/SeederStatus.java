package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Per-seeder progress recorded in {@link io.softa.starter.tenant.entity.TenantSeedProgress}: a seeder is
 * either DONE for a tenant or FAILED terminally. Absence of a row = not started / in flight.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum SeederStatus {
    DONE("Done"),
    // Not written today: a seeder failure is an exception, answered by redelivery, and an exhausted message
    // goes to a dead-letter topic nobody reads. The tenant-level FAILED comes from failTimedOut(); "which
    // seeder" comes from the [SEED_FAILURE] log line. Retained as the receiving half of the success=false
    // hook — see TenantProvisioningStatusService.markSeederFailed.
    FAILED("Failed"),
    ;

    @JsonValue
    private final String status;

}
