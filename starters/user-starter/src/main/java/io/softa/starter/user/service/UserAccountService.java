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