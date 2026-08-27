package io.softa.starter.user.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.dto.JoinEntry;
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
     * Revoke the outstanding invitation: kill the live link and put the account back to
     * {@link io.softa.starter.user.enums.AccountStatus#PENDING}, so it reads as "created,
     * not contacted" again and can be invited afresh.
     *
     * <p>Refuses once the account has a password: at that point the person has already
     * joined, and revoking would strand a working account in a pre-activation state.
     * Undoing THAT is off-boarding, not revocation.
     *
     * @param userId the account whose invitation is being withdrawn
     */
    void revokeInvitation(Long userId);

    /**
     * The /join entry check — whether this token may proceed to identity verification, and if not,
     * why (PRD §3.0's five ordered conditions).
     *
     * <p>Public: called before any session exists, by whoever opened the link.
     */
    JoinEntry inspectJoinToken(String rawToken);

    /**
     * Confirm joining: bind the verified person to the membership and activate it.
     *
     * <p>Separate from setting a password because they mean different things — see the
     * implementation for why activation waits for this call.
     */
    void confirmJoin(String rawToken, Long profileId);

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
