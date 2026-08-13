package io.softa.starter.user.service;

import io.softa.framework.base.context.UserInfo;
import java.util.List;

import io.softa.starter.user.dto.AuthenticationResult;
import io.softa.starter.user.dto.JoinVerification;
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
     * Authenticate by one-time code sent to a login identifier (email or dial-code mobile).
     *
     * <p>Replaces the per-channel {@code loginByEmailCode} / {@code loginByMobileCode}: the code
     * was sent to an identifier, and which KIND it is stopped mattering once identifiers became
     * properties of the person rather than of a company's account.
     */
    AuthenticationResult authenticateByCode(String identifier, String code);

    /** Authenticate by password against a login identifier. */
    AuthenticationResult authenticateByPassword(String identifier, String password);

    /** Whether this person still has to set a password (arrived by invitation or code only). */
    /**
     * Sends a verification code to whichever channel an invitation names, resolved from its token.
     *
     * <p>Lives here rather than on the invitation service because code issuance and its rate limits
     * are a login concern, and the invitation service is already a dependency of this one.
     *
     * @param channel {@code "email"} or {@code "mobile"}
     */
    void sendJoinCode(String rawToken, String channel);

    /**
     * Proves identity on the /join flow: verifies the code against the invitation's OWN address and
     * returns the person behind it, creating that person if this is their first company.
     *
     * <p>Separate from {@link #authenticateByCode} because that path resolves an existing person by
     * login identifier and then runs the company resolution. A first-time invitee has neither — no
     * profile, and no ACTIVE membership until they confirm — so it would reject exactly the people
     * this flow serves.
     *
     * @param channel {@code "email"} or {@code "mobile"} — which address the code was sent to
     */
    JoinVerification verifyJoinCode(String rawToken, String channel, String code);

    /**
     * Sets a first password during /join, where no session exists yet.
     *
     * <p>Authorized by the invitation rather than by a session, so it is deliberately narrow: it
     * refuses unless the profile's own login identifier is one the invitation names AND that
     * profile has no password. Without both checks a link-holder could name any profileId and
     * overwrite a stranger's password.
     */
    void setJoinPassword(String rawToken, Long profileId, String newPassword);

    /** Whether this person still has to set a password (arrived by invitation or code only). */
    boolean mustSetPassword(Long profileId);

    /**
     * Decide where a person lands right after joining a company.
     *
     * <p>Not simply "the membership they just joined": someone who already belonged elsewhere now
     * has two, and must still choose. Reusing the same resolution as authentication is what keeps
     * the two entry points from disagreeing.
     */
    AuthenticationResult afterJoin(Long profileId);

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