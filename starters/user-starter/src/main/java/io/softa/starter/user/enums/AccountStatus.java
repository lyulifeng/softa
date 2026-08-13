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
    PENDING_DELETION("PendingDeletion"),
    DELETED("Deleted"),
    BLACKLISTED("Blacklisted"),
    ;

    @JsonValue
    private final String status;
}
