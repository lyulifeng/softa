package io.softa.starter.user.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.entity.UserInvitation;

/**
 * Issues + accepts password-set-by-email tokens (invitations + self-service resets). Generates a
 * high-entropy one-time token, stores only its SHA-256 hash, emails the raw token by publishing a
 * framework {@code MailRequestMessage} (delivered by message-starter when present), and on accept sets the password
 * (activating an INVITED account). Extends {@link EntityService} so the 明细 (records) are browsable
 * through the generic model APIs.
 */
public interface UserInvitationService extends EntityService<UserInvitation, Long> {

    /**
     * Issue (or re-issue) an INVITE token for a user and email the set-password link. Any prior
     * PENDING token for the user is revoked so only the newest link works.
     *
     * @param userId    the account to invite (must have an email)
     * @param invitedBy the inviter's userId (null if system-initiated)
     */
    void invite(Long userId, Long invitedBy);

    /**
     * Self-service forgot-password: issue a PASSWORD_RESET token for the email. Silently no-ops when
     * the email is unknown (no account enumeration).
     */
    void forgotPassword(String email);

    /**
     * Set the password via a token (invite accept OR reset). Validates the token (PENDING +
     * unexpired), sets a fresh salted hash, activates an INVITED account, and marks the token
     * ACCEPTED. Public entry point — throws a generic {@code BusinessException} on any invalid token.
     */
    void acceptToken(String rawToken, String newPassword);

    /** Validate a token for the public set-password page (returns validity + email, no leak of why). */
    InvitationInfo inspectToken(String rawToken);
}
