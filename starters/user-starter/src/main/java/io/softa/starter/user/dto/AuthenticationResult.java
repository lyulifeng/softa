package io.softa.starter.user.dto;

import java.util.List;

import io.softa.framework.base.context.UserInfo;

/**
 * What authentication now answers: WHO you are, and what happens next.
 *
 * <p>It used to answer only "here is your UserInfo", which was enough while a person had exactly
 * one account. With several memberships possible, the caller needs three more things before it can
 * issue a session, and all three are decisions the client has to act on:
 *
 * <ul>
 *   <li>{@code profileId} — the authenticated person. Required for the follow-up calls
 *       ({@code listCompanies} / {@code selectCompany}); without it on the response the client has
 *       nothing to pass and those endpoints are unreachable.</li>
 *   <li>{@code mustSetPassword} — this person has no password yet (they arrived by invitation or
 *       verification code). The client must send them to Set Password and refuse to skip it,
 *       because a password-less person cannot come back through the password route.</li>
 *   <li>{@code companies} + {@code userInfo} — either exactly one enterable membership resolved
 *       (userInfo set, session issued) or a choice is needed (companies listed, userInfo null).</li>
 * </ul>
 *
 * @param profileId       the authenticated person
 * @param userInfo        the session payload when a single membership resolved; null when a choice
 *                        is pending
 * @param companies       the options when a choice is pending; empty when one already resolved
 * @param mustSetPassword whether the client must force the Set Password step first
 */
public record AuthenticationResult(Long profileId, UserInfo userInfo,
                                   List<MembershipOption> companies, boolean mustSetPassword) {

    public static AuthenticationResult resolved(Long profileId, UserInfo userInfo, boolean mustSetPassword) {
        return new AuthenticationResult(profileId, userInfo, List.of(), mustSetPassword);
    }

    public static AuthenticationResult choicePending(Long profileId, List<MembershipOption> companies,
            boolean mustSetPassword) {
        return new AuthenticationResult(profileId, null, List.copyOf(companies), mustSetPassword);
    }

    /** Whether a session can be issued straight away. */
    public boolean isResolved() {
        return userInfo != null;
    }
}
