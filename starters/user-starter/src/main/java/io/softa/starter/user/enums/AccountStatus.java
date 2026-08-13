package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Account Status
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
    UNVERIFIED("Unverified"),
    LOCKED("Locked"),
    FROZEN("Frozen"),
    /**
     * Off-boarded: the person has left this company, so this tenant's membership is
     * closed. Terminal — re-hiring creates a NEW employee record and a NEW account
     * (bound back to the same person), it does not revive this one.
     *
     * <p>Off-boarding must also release the work-email binding: once the address is
     * recycled, a new hire holding it could otherwise verify by code straight into
     * the previous holder's personal account.
     */
    DEACTIVATED("Deactivated"),
    PENDING_DELETION("PendingDeletion"),
    DELETED("Deleted"),
    BLACKLISTED("Blacklisted"),
    ;

    @JsonValue
    private final String status;
}
