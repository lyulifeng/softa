package io.softa.starter.user.dto;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.softa.framework.base.context.UserInfo;

/**
 * What authentication now answers: WHO you are, and what happens next.
 *
 * <p>It used to answer only "here is your UserInfo", which was enough while a person had exactly
 * one account. With several memberships possible, the caller needs more before a session can be
 * issued, and each is a decision the client acts on:
 *
 * <ul>
 *   <li>{@code profileId} — the authenticated person, for display. It is NOT what authorizes the
 *       follow-up calls: {@code authToken} is. profileId is not a secret, so a call that trusted
 *       it would let anyone name a stranger's id and be issued that stranger's session.</li>
 *   <li>{@code authToken} — a single-use, short-lived proof that authentication just succeeded for
 *       this person, present ONLY when a choice is pending (a resolved result already carries its
 *       session). {@code selectCompany} / {@code listCompanies} require it and read the person from
 *       it, so the second step of a multi-company login cannot be reached without passing the
 *       first.</li>
 *   <li>{@code mustSetPassword} — this person has no password yet (they arrived by invitation or
 *       code). The client must force Set Password, since a password-less person cannot return
 *       through the password route.</li>
 *   <li>{@code companies} + {@code userInfo} — either exactly one enterable membership resolved
 *       (userInfo set, session issued) or a choice is needed (companies listed, userInfo null).</li>
 *   <li>{@code signInRequired} — the membership was activated but NOTHING was issued: no session,
 *       no authToken. Only /join answers this, for a person who can already sign in some other way
 *       and has no password (see {@code LoginServiceImpl.confirmJoin}). The client sends them to
 *       the login page; every other result carries {@code false}.</li>
 * </ul>
 *
 * @param profileId       the authenticated person (display only; authToken is the credential)
 * @param userInfo        the session payload when a single membership resolved; null when a choice
 *                        is pending
 * @param companies       the options when a choice is pending; empty when one already resolved
 * @param mustSetPassword whether the client must force the Set Password step first
 * @param authToken       single-use proof of authentication for the company step; null once resolved
 * @param signInRequired  joined, but not signed in: the person must authenticate with their own
 *                        login before anything is issued
 */
public record AuthenticationResult(Long profileId, UserInfo userInfo, List<MembershipOption> companies,
                                   boolean mustSetPassword, String authToken, boolean signInRequired) {

    public static AuthenticationResult resolved(Long profileId, UserInfo userInfo, boolean mustSetPassword) {
        // Resolved means a session is being issued now, so no company step follows and no token is
        // needed — carrying one would be a live credential left lying in the response for nothing.
        return new AuthenticationResult(profileId, userInfo, List.of(), mustSetPassword, null, false);
    }

    public static AuthenticationResult choicePending(Long profileId, List<MembershipOption> companies,
            boolean mustSetPassword, String authToken) {
        return new AuthenticationResult(profileId, null, List.copyOf(companies), mustSetPassword, authToken, false);
    }

    /**
     * Joined, and nothing else. No userInfo (so {@link #isResolved()} is false and no session is
     * issued), no authToken (so the company step cannot be reached from this response), no
     * profileId (there is nothing for the client to act on with it), and mustSetPassword false —
     * the password is set from the person's own session, not from here.
     */
    public static AuthenticationResult requireSignIn() {
        return new AuthenticationResult(null, null, List.of(), false, null, true);
    }

    /** Whether a session can be issued straight away. */
    // Named on the wire on purpose: the client branches on "resolved", and a bean-convention
    // accident (isX) is too thin a thing for that contract to hang from.
    @JsonProperty("resolved")
    public boolean isResolved() {
        return userInfo != null;
    }
}
