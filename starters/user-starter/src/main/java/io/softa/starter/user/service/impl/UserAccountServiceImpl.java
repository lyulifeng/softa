package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import jakarta.validation.constraints.NotNull;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import java.time.LocalDateTime;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailRequestMessage;
import io.softa.framework.base.message.SmsRequestMessage;
import io.softa.framework.base.message.MessageScope;
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
import io.softa.starter.user.service.UserRoleRelService;
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

    /** Role grants are cleared on off-boarding and on reviving a membership. */
    @Autowired
    private UserRoleRelService roleRelService;

    /** Notifies the OLD address when work contacts are reset — see resetWorkContacts. */
    @Autowired
    private ApplicationEventPublisher eventPublisher;

    /** Optional: the contact-change notice names the company; absent tenant-starter → blank. */
    @Autowired(required = false)
    private io.softa.framework.orm.service.TenantInfoService tenantInfoService;

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

    // @CrossTenant: the company step runs BEFORE a tenant context exists — the whole point is to
    // list memberships ACROSS tenants so the person can pick one.
    @Override
    @CrossTenant
    public List<UserAccount> listMembershipsOf(Long profileId) {
        if (profileId == null) {
            return List.of();
        }
        return this.searchList(new Filters().eq(UserAccount::getProfileId, profileId)).stream()
                .filter(account -> account.getStatus() != AccountStatus.DEACTIVATED)
                .toList();
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
    @CrossTenant
    @Transactional
    public void offBoard(Long accountId) {
        UserAccount account = this.getById(accountId)
                .orElseThrow(() -> new BusinessException("Account not found."));
        if (offBoardWith(account)) {
            this.updateOne(account);
        }
    }

    /**
     * The three things off-boarding must do, on an already-loaded membership.
     *
     * <p>Separated from the lookup so the rules are testable without the ORM, and because their
     * ORDER is deliberate — see the comments inline.
     *
     * @return whether the membership changed (false when it was already closed)
     */
    boolean offBoardWith(UserAccount account) {
        if (account.getStatus() == AccountStatus.DEACTIVATED) {
            return false;   // idempotent: an HR workflow may legitimately fire twice
        }

        // ① Release the work contact from the person's login identifiers FIRST. Doing this before
        // the status write means a failure here cannot leave a closed membership whose address is
        // still a live login route — the order encodes which half is security-critical.
        this.releaseLoginIdentifiers(account);

        // ② Clear role grants. A closed membership holding live grants is a standing hole by
        // itself, and since a re-hire REVIVES this row, anything left here is silently inherited.
        clearRoleGrants(account);

        // ③ Close the membership.
        account.setStatus(AccountStatus.DEACTIVATED);
        return true;
    }

    /** Notifies the person that their sign-in contact changed. */
    private static final String TEMPLATE_CONTACT_RESET = "user.contact-reset";

    @SkipPermissionCheck
    @CrossTenant
    @Override
    @Transactional
    public void resetWorkContacts(Long userId, String newEmail, String newMobile, String reason) {
        Assert.notNull(userId, "userId is required");
        String email = StringUtils.trimToNull(newEmail);
        String mobile = StringUtils.trimToNull(newMobile);
        if (email == null && mobile == null) {
            throw new BusinessException("An account needs a work email or a work mobile.");
        }

        UserAccount account = this.getById(userId)
                .orElseThrow(() -> new BusinessException("User not found."));
        // The right person holds this membership — that is what separates this from unbinding. So
        // there is nothing to do on an account nobody holds yet.
        if (account.getProfileId() == null) {
            throw new BusinessException(
                    "This account is not linked to a person yet — invite it instead.");
        }
        this.getUserByEmail(email).filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessException("That work email already belongs to another account.");
                });

        String previousEmail = account.getEmail();
        String previousMobile = account.getMobile();

        // Move the LOGIN identifier with the contact, but only the value this company issued: a
        // personal login email is not ours to rewrite, so it compares before writing. Leaving it
        // behind would mean the person signs in with an address this company no longer knows, while
        // the recycled one becomes a route into their account for whoever receives it next.
        identityService.findByProfile(account.getProfileId()).ifPresent(identity -> {
            boolean moved = false;
            if (previousEmail != null && previousEmail.equalsIgnoreCase(identity.getLoginEmail())) {
                identity.setLoginEmail(email);
                moved = true;
            }
            if (previousMobile != null && previousMobile.equals(identity.getLoginMobile())) {
                identity.setLoginMobile(mobile);
                moved = true;
            }
            if (moved) {
                // updateOne(entity, false): clearing one channel means writing a null, which the
                // default overload drops — the old identifier would stay a live login route.
                identityService.updateOne(identity, false);
            }
        });

        account.setEmail(email);
        account.setMobile(mobile);
        this.updateOne(account, false);

        // The OLD address, not the new one. If this was not the person's own doing, the message has
        // to reach somewhere they can still read; telling only the new address informs whoever now
        // holds it. Password and profileId are untouched, so there is nothing to re-accept — this
        // is a notification, not an invitation.
        // Notify the OLD contacts on EVERY channel the account had, mail AND SMS — the same
        // fan-out invitations use. A person reachable only by work mobile must still learn their
        // sign-in was changed; telling only the email would leave exactly them uninformed. The
        // variables match the user.contact-reset templates (M1): who the account belongs to, which
        // company changed it, and when. nickname (already on the account) stands in for the name
        // rather than loading the profile, which is more than a notification warrants.
        Long tenantId = ContextHolder.getContext().getTenantId();
        MessageScope scope = tenantId != null ? MessageScope.TENANT : MessageScope.PLATFORM;
        String companyName = tenantId == null || tenantInfoService == null ? ""
                : StringUtils.defaultString(tenantInfoService.getTenantName(tenantId));
        Map<String, Object> vars = Map.of(
                "employeeName", StringUtils.defaultString(account.getNickname()),
                "companyName", companyName,
                "time", LocalDateTime.now().toString());
        if (StringUtils.isNotBlank(previousEmail)) {
            eventPublisher.publishEvent(new MailRequestMessage(
                    List.of(previousEmail), TEMPLATE_CONTACT_RESET, vars, tenantId, scope));
        }
        if (StringUtils.isNotBlank(previousMobile)) {
            eventPublisher.publishEvent(new SmsRequestMessage(
                    List.of(previousMobile), TEMPLATE_CONTACT_RESET, vars));
        }
        log.info("Work contacts of account {} reset. Reason: {}", userId, reason);
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public boolean isWorkContactShared(String contact) {
        if (StringUtils.isBlank(contact)) {
            return false;
        }
        // Counted across tenants: the same number handed to workers in two companies is still one
        // number, and the ambiguity it creates is not confined to a tenant.
        long asEmail = this.count(new Filters().eq(UserAccount::getEmail, contact));
        long asMobile = this.count(new Filters().eq(UserAccount::getMobile, contact));
        return asEmail + asMobile > 1;
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public boolean releaseLoginIdentifiers(UserAccount account) {
        if (account == null) {
            return false;
        }
        return identityService.findByProfile(account.getProfileId()).map(identity -> {
            boolean released = false;
            if (StringUtils.isNotBlank(account.getEmail())
                    && account.getEmail().equalsIgnoreCase(identity.getLoginEmail())) {
                identity.setLoginEmail(null);
                released = true;
            }
            if (StringUtils.isNotBlank(account.getMobile())
                    && account.getMobile().equals(identity.getLoginMobile())) {
                identity.setLoginMobile(null);
                released = true;
            }
            if (released) {
                // updateOne(entity) drops null keys — the one thing this write is FOR. The
                // overload that keeps them is not optional here.
                identityService.updateOne(identity, false);
                log.info("Released work contact(s) from identity {} of account {}.",
                        identity.getId(), account.getId());
            }
            return released;
        }).orElse(false);
    }

    @Override
    @CrossTenant
    @Transactional
    public Optional<UserAccount> reviveMembership(Long profileId, String workEmail, String workMobile) {
        if (profileId == null) {
            return Optional.empty();
        }
        Optional<UserAccount> closed = this.searchList(new Filters()
                        .eq(UserAccount::getProfileId, profileId)
                        .eq(UserAccount::getStatus, AccountStatus.DEACTIVATED)).stream()
                .findFirst();
        if (closed.isEmpty()) {
            return Optional.empty();
        }
        UserAccount account = closed.get();
        reviveWith(account, workEmail, workMobile);
        this.updateOne(account);
        log.info("Revived membership {} for profile {} — reset to PENDING, awaiting a new invitation.",
                account.getId(), profileId);
        return Optional.of(account);
    }

    /**
     * Reset a closed membership for someone re-joining, on an already-loaded row.
     *
     * <p>Nothing from the previous stint may leak into the new one: new work contacts, no
     * activation (they must accept a fresh invitation), no roles. The employment history that DOES
     * carry over lives on the employee record, which is created anew.
     */
    void reviveWith(UserAccount account, String workEmail, String workMobile) {
        account.setEmail(workEmail);
        account.setMobile(workMobile);
        account.setStatus(AccountStatus.PENDING);
        account.setActivationTime(null);
        clearRoleGrants(account);
    }

    /**
     * Drop every role grant held by a membership.
     *
     * <p>Both the {@code roles} field and the {@code UserRoleRel} rows: the former is what the UI
     * edits, the latter is what the permission snapshot reads, and leaving either behind means
     * "no roles" in one place and live grants in the other.
     */
    private void clearRoleGrants(UserAccount account) {
        roleRelService.deleteByFilters(new Filters().eq("userId", account.getId()));
        if (account.getRoles() != null && !account.getRoles().isEmpty()) {
            account.setRoles(List.of());
        }
    }

    @Override
    @Transactional
    public void changeMyPassword(String currentPassword, String newPassword) {
        Assert.notBlank(currentPassword, "Old password cannot be empty.");
        Assert.notBlank(newPassword, "New password cannot be empty.");
        // Strength (B7) is enforced inside identityService.setPassword, the choke point every
        // password write passes through — not repeated here.

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
    public boolean mustSetMyPassword() {
        Long userId = ContextHolder.getContext().getUserId();
        if (userId == null) {
            // No session, nothing owed — the caller is not the person this could apply to.
            return false;
        }
        return this.getById(userId)
                .map(account -> account.getProfileId() != null
                        && identityService.findByProfile(account.getProfileId())
                                .map(identity -> StringUtils.isBlank(identity.getPassword()))
                                // No credentials row at all: a data fault, and NOT a reason to
                                // force a password screen the set-password call would then refuse.
                                .orElse(false))
                .orElse(false);
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
        // Strength (B7) is enforced inside identityService.setPassword — see changeMyPassword.
        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        identityService.setPassword(user, newPassword);

        log.info("User ID {} password was reset by admin.", userId);
        return true;
    }

    @Override
    @Transactional
    public void freezeAccount(Long userId, String reason) {
        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        if (user.getStatus() == AccountStatus.FROZEN) {
            return;   // idempotent: an HR workflow may legitimately fire twice
        }
        if (user.getStatus() != AccountStatus.ACTIVE && user.getStatus() != AccountStatus.LOCKED) {
            throw new BusinessException("Only an active account can be frozen.");
        }
        // Clear the password lock on the way through (§2.3 "Freeze on Locked — clear the lock
        // first"). Leaving it would mean that lifting the freeze hands back an account the lockout
        // still refuses, and the administrator who lifted it cannot see why.
        identityService.findByProfile(user.getProfileId()).ifPresent(identity -> {
            if (identity.getPasswordLockedUntil() != null) {
                identity.setPasswordLockedUntil(null);
                // updateOne(entity, false): clearing the lock means writing a null, which the
                // default overload drops.
                identityService.updateOne(identity, false);
            }
            identityService.clearPasswordFailures(identity.getId());
        });
        user.setStatus(AccountStatus.FROZEN);
        this.updateOne(user);
        log.info("Account {} frozen. Reason: {}", userId, reason);
    }

    @Override
    @Transactional
    public void unfreezeAccount(Long userId, String reason) {
        UserAccount user = this.getById(userId).orElseThrow(() -> new BusinessException("User not found."));
        if (user.getStatus() != AccountStatus.FROZEN) {
            // Not idempotent-by-silence on purpose: an INVITED or DEACTIVATED account flipped to
            // ACTIVE here would land in a state its own flow never reaches — invited-but-active,
            // or off-boarded-but-active.
            throw new BusinessException("Only a frozen account can be unfrozen.");
        }
        user.setStatus(AccountStatus.ACTIVE);
        this.updateOne(user);
        log.info("Account {} unfrozen. Reason: {}", userId, reason);
    }

    @Override
    @Transactional
    public void unfreezeAccounts(List<Long> userIds, String reason) {
        Filters filters = new Filters().in(UserAccount::getId, userIds)
                // Bulk path, so the per-row guard above cannot run — the filter carries it instead.
                // Without this a bulk "unfreeze everything selected" would activate invited and
                // off-boarded rows caught in the selection.
                .eq(UserAccount::getStatus, AccountStatus.FROZEN);
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