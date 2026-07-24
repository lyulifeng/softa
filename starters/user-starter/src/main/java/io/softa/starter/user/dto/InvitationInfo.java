package io.softa.starter.user.dto;

import lombok.Data;

import io.softa.starter.user.enums.InvitationPurpose;

/**
 * Result of validating an invitation token (for the public set-password page). Never leaks WHY a
 * token is invalid (expired vs used vs unknown) — the page only needs {@code valid} + the email to
 * greet the holder of a still-valid link.
 */
@Data
public class InvitationInfo {

    private boolean valid;
    private String email;
    private InvitationPurpose purpose;

    public static InvitationInfo invalid() {
        return new InvitationInfo();
    }

    public static InvitationInfo valid(String email, InvitationPurpose purpose) {
        InvitationInfo info = new InvitationInfo();
        info.setValid(true);
        info.setEmail(email);
        info.setPurpose(purpose);
        return info;
    }
}
