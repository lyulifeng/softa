package io.softa.starter.user.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.starter.user.entity.UserInvitation;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/** Why a {@link UserInvitation} token was issued. */
@Getter
@AllArgsConstructor
@OptionSet
public enum InvitationPurpose {
    INVITE("Invite"),                 // Ops onboards a new (INVITED) account — first-time password set
    PASSWORD_RESET("PasswordReset"),  // existing ACTIVE user self-service forgot-password
    REINVITE("Reinvite"),             // the membership was bound to the WRONG person — see W5
    ;

    @JsonValue
    private final String purpose;
}
