package io.softa.starter.user.service;

import io.softa.framework.base.context.UserInfo;
import java.util.List;

import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.dto.MembershipOption;

/**
 * UserAccount Model Service Interface
 */
public interface LoginService {

    /**
     * Send email verification code
     *
     * @param email Email address
     */
    void sendEmailCode(String email);

    /**
     * Send mobile verification code
     *
     * @param mobile Mobile number
     */
    void sendMobileCode(String mobile);

    /**
     * User login by email verification code
     *
     * @param email Email address
     * @param code  Verification code
     * @return UserInfo
     */
    UserInfo loginByEmailCode(String email, String code);

    /**
     * User login by mobile verification code
     *
     * @param mobile Mobile number
     * @param code   Verification code
     * @return UserInfo
     */
    UserInfo loginByMobileCode(String mobile, String code);

    /**
     * Generate a new session ID for a user
     *
     * @param userId User ID
     * @return Session ID
     */
    String generateSessionId(Long userId);
    /**
     * The companies this person may log into, for the "choose your company" step.
     *
     * <p>Authentication answers WHO; this answers WHERE. They are separate calls because one
     * person can belong to several companies while a session must carry exactly one membership.
     *
     * @param profileId the authenticated person
     * @return their memberships, off-boarded ones excluded, non-ACTIVE ones listed but flagged
     *         unselectable (so a frozen company is visibly present rather than silently missing)
     */
    List<MembershipOption> listCompanies(Long profileId);

    /**
     * Resolve which membership an authenticated person lands in.
     *
     * <p>0 → refuse; exactly 1 → that one (so a single-company person sees no extra step, which
     * is today's behaviour unchanged); more than 1 → refuse and let the caller present
     * {@link #listCompanies}.
     *
     * @throws io.softa.framework.base.exception.BusinessException when the person belongs to no
     *         company, or to several and must choose
     */
    Long resolveSingleMembership(Long profileId);

    /**
     * Verify that this membership really belongs to this person, then hand back its account id
     * for session issuance.
     *
     * <p>The ownership check is the security point: without it, anyone who authenticated as
     * themselves could name someone else's accountId and be issued a session in a company they
     * have no membership of.
     */
    Long selectCompany(Long profileId, Long accountId);
    /**
     * User login by email and password
     *
     * @param email    Email address
     * @param password Password
     * @return UserInfo
     */
    UserInfo loginByEmailAndPassword(String email, String password);

    /**
     * Forgot password — issue a self-service password-reset token and email the set-password link.
     *
     * @param email registered email
     */
    void forgetPassword(String email);

    /**
     * Set the password via a token (invitation-accept or forgot-password reset).
     *
     * @param token       the emailed one-time token
     * @param newPassword the new password
     */
    void resetPassword(String token, String newPassword);

    /**
     * Validate a token for the public set-password page.
     *
     * @param token the emailed one-time token
     * @return validity + the email to greet the holder (no leak of why an invalid token failed)
     */
    InvitationInfo inviteInfo(String token);
}