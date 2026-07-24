package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.starter.user.entity.UserInvitation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/** Lifecycle of a {@link UserInvitation} token. */
@Getter
@AllArgsConstructor
@OptionSet
public enum InvitationStatus {
    PENDING("Pending"),     // issued, not yet used, not expired
    ACCEPTED("Accepted"),   // password set via the token (terminal)
    EXPIRED("Expired"),     // past expiresAt without use (terminal)
    REVOKED("Revoked"),     // superseded by a resend or manually revoked (terminal)
    ;

    @JsonValue
    private final String status;
}
