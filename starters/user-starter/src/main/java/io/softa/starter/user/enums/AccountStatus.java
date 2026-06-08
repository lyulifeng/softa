package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Account Status
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Account Status")
public enum AccountStatus {
    @OptionItem(label = "Active")
    ACTIVE("Active"),
    @OptionItem(label = "Unverified")
    UNVERIFIED("Unverified"),
    @OptionItem(label = "Locked")
    LOCKED("Locked"),
    @OptionItem(label = "Frozen")
    FROZEN("Frozen"),
    @OptionItem(label = "Pending Deletion")
    PENDING_DELETION("PendingDeletion"),
    @OptionItem(label = "Deleted")
    DELETED("Deleted"),
    @OptionItem(label = "Blacklisted")
    BLACKLISTED("Blacklisted"),
    ;

    @JsonValue
    private final String status;
}
