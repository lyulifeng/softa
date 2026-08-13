package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.dto.UserAccountDTO;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

/**
 * UserAccount Model Service Implementation
 */
@Slf4j
@Service
public class UserAccountServiceImpl extends EntityServiceImpl<UserAccount, Long> implements UserAccountService {

    @Autowired
    private UserProfileService profileService;

    @Autowired
    private UserIdentityService identityService;

    /**
     * Every single-entity account write funnels through here, so this is the one place that has to
     * drop the cached {@code UserInfo} — covering not just this class's own lock / unlock / password
     * paths but external callers too, above all {@code UserInvitationService.acceptToken}, which
     * flips INVITED → ACTIVE when the invitee sets their password. Missing that eviction left the
     * login gate (reads the account row) and {@code ContextBuilder} (reads this cache) disagreeing
     * for the cache's one-month TTL: the invitee authenticated successfully and was then bounced
     * back to the login screen by the next request, which saw {@code active=false}, cleared the
     * session, and reported "Invalid session ID". The reverse case is worse — a freshly frozen
     * account kept working, because the per-request status gate never saw the new status.
     */
    @Override
    public boolean updateOne(UserAccount entity) {
        boolean updated = super.updateOne(entity);
        if (updated && entity != null) {
            profileService.evictUserInfo(entity.getId());
        }
        return updated;
    }

    // @CrossTenant: login / forgot-password resolve an account by credential BEFORE a tenant
    // context exists (UserAccount is multiTenant); without it the ORM would filter by the absent
    // tenant and never find the account. Called only by other beans → the AOP proxy applies (no self).
    @Override
    @CrossTenant
    public Optional<UserAccount> getUserByEmail(String email) {
        Filters filters = new Filters().eq(UserAccount::getEmail, email);
        return this.searchOne(filters);
    }

    @Override
    @CrossTenant
    public Optional<UserAccount> getUserByMobile(String mobile) {
        Filters filters = new Filters().eq(UserAccount::getMobile, mobile);
        return this.searchOne(filters);
    }

    /**
     * Self-registration (OAuth only, since the email+password route was removed) creates
     * accounts with no tenant
     * context, which under multi-tenancy would land as tenant-less orphans. So it is allowed only
     * in single-tenant mode; multi-tenant deployments create/invite accounts via an administrator.
     */
    private void assertSelfRegistrationAllowed() {
        if (SystemConfig.env.isEnableMultiTenancy()) {
            throw new BusinessException(
                    "Self-registration is disabled in multi-tenant mode; accounts are created or invited by an administrator.");
        }
    }

    private UserAccount buildUserAccount(UserAccountDTO accountInfo) {
        UserAccount userAccount = new UserAccount();
        userAccount.setEmail(accountInfo.getEmail());
        userAccount.setMobile(accountInfo.getMobile());
        userAccount.setUsername(accountInfo.getUsername());
        userAccount.setNickname(accountInfo.getNickname());
        userAccount.setStatus(AccountStatus.ACTIVE);
        // tenant_id is stamped by the framework on insert (UserAccount is multiTenant); a manual
        // set here would write null under the anonymous/cross-tenant context of self-registration.
        return userAccount;
    }

    /**
     * Register new user
     * Create user account and user profile, return UserInfo
     *
     * @param accountInfo User account information
     * @param profileInfo User profile information
     * @return UserInfo
     */
    // @SkipPermissionCheck: provisioning writes rows this call mints — the account, and through
    // registerUserProfile the person and their credentials. Row scope has no answer for ids that do
    // not exist yet, and fails closed on the anchorless ones. See registerUserProfile for the full
    // argument; the waiver covers provisioning only, never the business entity that asked for it.
    @SkipPermissionCheck
    @Override
    @Transactional
    public UserInfo registerNewUser(@NotNull UserAccountDTO accountInfo, @NotNull UserProfileDTO profileInfo) {
        assertSelfRegistrationAllowed();
        Assert.notBlank(accountInfo.getUsername(), "Username cannot be blank");
        try {
            // Create user account
            UserAccount userAccount = this.buildUserAccount(accountInfo);
            Long userId = this.createOne(userAccount);

            // Create user profile and return UserInfo
            return profileService.registerUserProfile(userId, profileInfo);
        } catch (Exception e) {
            throw new BusinessException("User registration failed: {0}", e.getMessage(), e);
        }
    }

    /**
     * Register new user (legacy method for backward compatibility)
     * Create user account and user profile, return UserInfo
     *
     * @param email Email
     * @param mobile Mobile
     * @param password Password
     * @return UserInfo
     */
    // @SkipPermissionCheck: the path that pairs an employee with a login. Without it, a role granted
    // "create employee" — and nothing on the user models, which the role wizard does not require it
    // to hold — creates the Employee row and then fails on the person behind it, leaving the caller
    // with a rolled-back create and an error naming a model they never asked to touch. See
    // registerUserProfile.
    @SkipPermissionCheck
    @Override
    @Transactional
    public UserInfo registerInvitedUser(String email, String mobile, String fullName) {
        // Both identifiers are login credentials, so both are checked ACROSS tenants (the lookups
        // are @CrossTenant — same reasoning as AdminProvisioningService's email check). Email is
        // also backed by uk_user_account_email, but a mobile-only account has no unique index
        // behind it: without this check, importing two employees with the same phone and no email
        // silently minted two accounts for one credential.
        if (StringUtils.isNotBlank(email) && this.getUserByEmail(email).isPresent()) {
            throw new BusinessException("Email already exists: " + email);
        }
        if (StringUtils.isNotBlank(mobile) && this.getUserByMobile(mobile).isPresent()) {
            throw new BusinessException("Mobile already exists: " + mobile);
        }
        UserAccountDTO accountInfo = new UserAccountDTO();
        UserProfileDTO profileInfo = new UserProfileDTO();
        accountInfo.setEmail(email);
        accountInfo.setMobile(mobile);
        // Username = email if present, else mobile (mirrors registerNewUser)
        String identifier = StringUtils.isNotBlank(email) ? email : mobile;
        accountInfo.setUsername(identifier);
        // Display name: caller-supplied fullName (e.g. an employee's real name) when present,
        // else fall back to the login identifier so account/profile display is never blank.
        String displayName = StringUtils.isNotBlank(fullName) ? fullName : identifier;
        accountInfo.setNickname(displayName);
        profileInfo.setFullName(displayName);

        UserAccount userAccount = this.buildUserAccount(accountInfo);
        // PENDING, no password. Creating an account does NOT contact anyone — the explicit
        // Invite action does, and that is what flips PENDING -> INVITED. Landing on INVITED
        // here would claim an invitation was sent when none was, which is exactly the
        // distinction the account list needs to show ("created" vs "contacted").
        userAccount.setStatus(AccountStatus.PENDING);
        Long userId = this.createOne(userAccount);

        return profileService.registerUserProfile(userId, profileInfo);
    }

    @Override
    @Transactional
    public void changeMyPassword(String currentPassword, String newPassword) {
        Assert.notBlank(currentPassword, "Old password cannot be empty.");
        Assert.notBlank(newPassword, "New password cannot be empty.");
        // TODO: Add password strength validation

        Long userId = ContextHolder.getContext().getUserId();
        Assert.notNull(userId, "Cannot change password without logged-in user context.");

        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("Current user not found."));

        // The password is the PERSON's, so changing it here changes it for every company they
        // belong to. That is the intended meaning of one global credential.
        UserIdentity identity = identityService.requireIdentity(user);
        if (!identityService.matchesPassword(identity, currentPassword)) {
            throw new BusinessException("Incorrect old password.");
        }
        if (identityService.matchesPassword(identity, newPassword)) {
            throw new BusinessException("New password cannot be the same as the old password.");
        }
        identityService.setPassword(identity.getId(), newPassword);

        log.info("User ID {} changed their password successfully (identity {}).", userId, identity.getId());
    }

    @Override
    @Transactional
    public void setMyFirstPassword(String newPassword) {
        Assert.notBlank(newPassword, "New password cannot be empty.");
        Long userId = ContextHolder.getContext().getUserId();
        Assert.notNull(userId, "Cannot set a password without logged-in user context.");

        UserAccount user = this.getById(userId)
                .orElseThrow(() -> new BusinessException("Current user not found."));
        UserIdentity identity = identityService.requireIdentity(user);
        if (StringUtils.isNotBlank(identity.getPassword())) {
            // Whoever holds this session already had a way in that they must prove again. Letting
            // the call through here would turn "I forgot my password" into "I am already inside",
            // which is the one thing the change-password flow exists to prevent.
            throw new BusinessException(
                    "A password is already set — use change password instead.");
        }
        // Strength is enforced inside setPassword, against this person's own contact details.
        identityService.setPassword(identity.getId(), newPassword);

        log.info("User ID {} set their first password (identity {}).", userId, identity.getId());
    }

    @Override
    @Transactional
    public boolean forceResetPassword(Long userId, String newPassword) {
        Assert.notBlank(newPassword, "New password cannot be empty.");
        // TODO: Add password strength validation

        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        identityService.setPassword(user, newPassword);

        log.info("User ID {} password was reset by admin.", userId);
        return true;
    }

    @Override
    @Transactional
    public void lockAccount(Long userId) {
        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        user.setStatus(AccountStatus.LOCKED);
        this.updateOne(user);
    }

    @Override
    @Transactional
    public void unlockAccount(Long userId, String reason) {
        // TODO: Log the unlock reason for auditing purposes
        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        user.setStatus(AccountStatus.ACTIVE);
        this.updateOne(user);
    }

    @Override
    @Transactional
    public void unlockAccounts(List<Long> userIds, String reason) {
        Filters filters = new Filters().in(UserAccount::getId, userIds);
        UserAccount updateEntity = new UserAccount();
        updateEntity.setStatus(AccountStatus.ACTIVE);
        this.updateByFilter(filters, updateEntity);
        // updateByFilter bypasses updateOne, so evict here as well — otherwise a bulk-unlocked
        // account stays `active=false` in the cache and every request terminates its session.
        if (userIds != null) {
            userIds.forEach(profileService::evictUserInfo);
        }
    }
}