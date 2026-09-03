package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Account Status — a single axis of six values (D-series / A4).
 *
 * <p>{@code Unverified}, {@code PendingDeletion}, {@code Deleted} and {@code Blacklisted} were
 * removed: nothing ever wrote them (verified against the live dev and test schemas — zero rows in
 * either) and each one duplicated a distinction the axis already makes, so they could only ever
 * produce a state no operation knew how to leave.
 *
 * <p>The column is {@code varchar}, not a database enum, so their removal needs no DDL. It does
 * leave stale rows in the {@code AccountStatus} option set, which is create-or-update and never
 * deletes — those are cleaned by an ops script, not by the scanner.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum AccountStatus {
    ACTIVE("Active"),
    /**
     * Created but never invited: the account exists (so HR can maintain it and
     * an employee record can point at it) and no invitation has been sent yet.
     * Distinguishing this from {@link #INVITED} is what lets the account list
     * answer "has this person been contacted?" — creating and inviting are
     * separate actions.
     */
    PENDING("Pending"),
    INVITED("Invited"),
    LOCKED("Locked"),
    FROZEN("Frozen"),
    /**
     * Off-boarded: the person has left this company, so this tenant's membership is
     * closed. NOT terminal — re-hiring REVIVES this row (see
     * {@code UserAccountService.reviveMembership}), because {@code (tenantId, profileId)}
     * is unique: one person has at most one membership per company, and the database
     * enforces that instead of application code racing with itself. The row is reset to
     * a fresh {@code PENDING}; the employment history that carries over lives on the
     * employee record, which IS created anew.
     *
     * <p>Off-boarding must also release the work-email binding: once the address is
     * recycled, a new hire holding it could otherwise verify by code straight into
     * the previous holder's personal account.
     */
    DEACTIVATED("Deactivated"),
    ;

    @JsonValue
    private final String status;
}
