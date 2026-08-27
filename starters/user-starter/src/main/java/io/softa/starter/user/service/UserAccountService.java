package io.softa.starter.user.service;

import java.util.List;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;

import io.softa.framework.base.context.UserInfo;
import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.dto.UserAccountDTO;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserAccount;

/**
 * UserAccount Model Service Interface
 */
public interface UserAccountService extends EntityService<UserAccount, Long> {

    /**
     * Every membership belonging to one person, across all companies.
     *
     * <p>{@code @CrossTenant} by nature: it is asked after authenticating but before a company is
     * chosen, so there is no tenant context to scope it to — that is the whole point of the call.
     *
     * <p>Off-boarded memberships are excluded: a former employee must not see their previous
     * company in the picker.
     */
    List<UserAccount> listMembershipsOf(Long profileId);

    /**
     * Get user by email
     *
     * @param email email
     * @return UserAccount
     */
    Optional<UserAccount> getUserByEmail(String email);

    /**
     * Get user by mobile
     *
     * @param mobile mobile
     * @return UserAccount
     */
    Optional<UserAccount> getUserByMobile(String mobile);

    /**
     * Register a new user
     *
     * @param accountInfo User account information
     * @param profileInfo User profile information
     * @return UserInfo
     */
    UserInfo registerNewUser(@NotNull UserAccountDTO accountInfo, @NotNull UserProfileDTO profileInfo);


    /**
     * Register an account for someone who has not been contacted yet — an
     * {@link io.softa.starter.user.enums.AccountStatus#PENDING} account with NO password.
     * Sending the invitation is a separate, explicit action
     * ({@code UserInvitationService.invite}) which flips the account to
     * {@link io.softa.starter.user.enums.AccountStatus#INVITED}; accepting the link then
     * sets the password and activates it.
     *
     * @param email    Email (used as the username when present)
     * @param mobile   Mobile (used as the username when email is absent)
     * @param fullName Display name for the account (nickname) and profile; when blank, falls
     *                 back to the login identifier (email or mobile)
     * @return UserInfo
     */
    UserInfo registerInvitedUser(String email, String mobile, String fullName);

    /**
     * Off-board a membership: close it and strip what must not outlive it (A7 / S3, PRD W6).
     *
     * <p>Three things happen together because leaving any one of them out is a real defect:
     *
     * <ol>
     *   <li><b>status → DEACTIVATED</b> — the membership is over;</li>
     *   <li><b>role grants cleared</b> — a closed membership carrying live grants is a standing
     *       hole, and because a re-hire REVIVES this same row, grants left behind would be
     *       silently inherited by the returning employee;</li>
     *   <li><b>the work email released from the person's login identifiers</b> — otherwise, once
     *       the address is recycled, a new hire holding it could verify by code straight into the
     *       previous holder's personal account.</li>
     * </ol>
     *
     * <p>Idempotent: off-boarding an already-off-boarded membership changes nothing.
     *
     * @param accountId the membership to close
     */
    void offBoard(Long accountId);

    /**
     * Release from the person's login identifiers whatever THIS company issued them.
     *
     * <p>Shared by the two operations that end a binding — off-boarding and unbind-and-re-invite —
     * because both create the same hazard: once the address is recycled, whoever receives it next
     * could verify by code straight into the previous holder's personal account.
     *
     * <p>Only the value this company issued is reclaimed. A personal login email is not ours to
     * take, which is why it compares before clearing rather than blindly nulling.
     *
     * @return whether anything was released
     */
    boolean releaseLoginIdentifiers(UserAccount account);

    /**
     * Prepare a membership for someone re-joining this company, reusing the closed row.
     *
     * <p>Re-hire revives rather than inserts, because {@code (tenantId, profileId)} is unique —
     * one person has at most one membership per company, which is what lets the database enforce
     * it instead of application code racing with itself. The row is reset to a fresh
     * {@code PENDING} (new work contacts, no activation, no roles) so nothing from the previous
     * stint leaks into the new one; employment history lives on the employee record, which IS
     * created anew.
     *
     * @return the revived membership, or empty when this person has no closed membership here
     */
    java.util.Optional<UserAccount> reviveMembership(Long profileId, String workEmail, String workMobile);

    /**
     * Change the current user's password
     *
     * @param currentPassword Current password
     * @param newPassword New password
     */
    void changeMyPassword(String currentPassword, String newPassword);

    /**
     * Sets a FIRST password for the logged-in person — the actionable half of
     * {@code AuthenticationResult.mustSetPassword}.
     *
     * <p>Separate from {@link #changeMyPassword} because that method demands the current password,
     * which is precisely what these people do not have: they arrived by invitation or verification
     * code, so requiring it would make the forced step impossible to complete.
     *
     * <p>Refuses when a password already exists. Without that check this would be an
     * authenticated password reset with no proof of the old one — any hijacked session could
     * silently take the account over.
     */
    void setMyFirstPassword(String newPassword);

    /**
     * Whether the logged-in person still owes a first password.
     *
     * <p>Exists so the forced Set Password step survives a page reload. Authentication reports
     * {@code mustSetPassword} once, in its response; a client that only remembers it from there
     * loses the requirement the moment the person refreshes or closes the tab — and since a
     * password-less person cannot come back through the password route, skipping it that way locks
     * them out of their own account rather than merely postponing a screen.
     */
    boolean mustSetMyPassword();

    /**
     * Force reset user password (admin operation)
     *
     * @param userId User ID
     * @param newPassword New password
     * @return Success status
     */
    boolean forceResetPassword(Long userId, String newPassword);

    /**
     * Lock a user account
     */
    void lockAccount(Long userId);

    /**
     * Unlock a user account
     */
    void unlockAccount(Long userId, String reason);

    void unlockAccounts(List<Long> userIds, String reason);
}