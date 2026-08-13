package io.softa.starter.user.service.impl;

import java.util.Objects;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.RandomStringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.framework.base.utils.UUIDUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.LoginService;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserInvitationService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

/**
 * UserAccount Model Service Implementation
 */
@Slf4j
@Service
public class LoginServiceImpl implements LoginService {

    @Autowired
    private CacheService cacheService;

    @Autowired
    private UserAccountService accountService;

    @Autowired
    private UserProfileService profileService;

    @Autowired
    private UserIdentityService identityService;

    @Autowired(required = false)
    private TenantInfoService tenantInfoService;

    @Autowired
    private UserInvitationService invitationService;

    /** Send / attempt limits for one-time codes (PRD D2) — see the class for why each exists. */
    @Autowired
    private VerificationCodeGuard codeGuard;

    /**
     * Tenant lifecycle gate at login: only ACTIVE tenants may log in. Enforced at the single
     * session-issuance choke point ({@link #generateSessionId}), so every login flow — password,
     * email/mobile code, and OAuth — is covered, and the user gets a clear reason before a
     * session is issued (rather than a session the per-request gate would immediately reject).
     * No-op when multi-tenancy is disabled.
     *
     * @param userId the user being logged in (its tenantId is resolved here)
     */
    private void validateTenantActive(Long userId) {
        if (!SystemConfig.env.isEnableMultiTenancy()) {
            return;
        }
        UserInfo userInfo = profileService.getUserInfo(userId);
        Long tenantId = userInfo == null ? null : userInfo.getTenantId();
        if (tenantInfoService == null) {
            throw new BusinessException("Login denied: tenant is not active.");
        }
        // Not-yet-built is checked FIRST, for its message. Both states now live on one field, so a tenant mid
        // setup is also not ACTIVE — asking "is it active" first would answer every in-flight tenant with the
        // generic "not active" and lose the one thing the user can act on ("it is still being set up, wait").
        //
        // The check earns its place beyond the wording: letting someone in mid-seed shows a workspace whose
        // roles and org masters are still arriving over MQ, and lets them create records pointing at masters
        // that do not exist yet. It is also what makes discarding a failed setup safe — if nobody can get in
        // before it goes ACTIVE, every row in the tenant came from a seeder.
        if (!tenantInfoService.isTenantProvisioned(tenantId)) {
            throw new BusinessException("Login denied: this workspace is still being set up.");
        }
        if (!tenantInfoService.isTenantActive(tenantId)) {
            throw new BusinessException("Login denied: tenant is not active.");
        }
    }

    /**
     * Account lifecycle gate at login: only an ACTIVE account may obtain a session.
     * Sits at the same session-issuance choke point as {@link #validateTenantActive},
     * so every login flow (password / email code / mobile code / OAuth / Apple) is
     * covered. It runs AFTER credentials are verified, so returning the specific
     * reason is safe — the caller has already proven identity, so this is not an
     * account-enumeration channel.
     *
     * <p>Closes the gap where an employee off-boarded to INACTIVE (mirrored onto
     * UserAccount.status = Frozen) could still authenticate, because login only
     * checked the password and never the account state.
     */
    private void validateAccountActive(Long userId) {
        UserAccount account = accountService.getById(userId)
                .orElseThrow(() -> new BusinessException("Login denied: account not found."));
        if (account.getStatus() == AccountStatus.ACTIVE) {
            return;
        }
        throw new BusinessException(accountDeniedMessage(account.getStatus()));
    }

    /** Human-readable reason for refusing login to a non-ACTIVE account. */
    private static String accountDeniedMessage(AccountStatus status) {
        String reason = switch (status == null ? AccountStatus.FROZEN : status) {
            case FROZEN, PENDING_DELETION, DELETED -> "your account has been deactivated";
            case DEACTIVATED -> "your membership of this company has ended";
            case LOCKED -> "your account is locked";
            case BLACKLISTED -> "your account has been blocked";
            case PENDING -> "your account has not been invited yet — ask your administrator to send the invitation";
            case INVITED -> "your account invitation has not been accepted yet";
            case UNVERIFIED -> "your account has not been verified yet";
            default -> "your account is not active";
        };
        return "Login denied: " + reason + ".";
    }

    public static String buildLoginCodeKey(String identifier) {
        // Partition by login scenario
        return RedisConstant.VERIFICATION_CODE + "login:" + identifier;
    }

    /**
     * Issue a code for this identifier, subject to the send limits.
     *
     * <p>The rate check runs BEFORE generating: an over-limit request must be refused without
     * overwriting a code the user may still be typing.
     */
    private String generateNumericCode(String identifier) {
        codeGuard.beforeSend(identifier);
        String code = RandomStringUtils.insecure()
                .nextNumeric(VerificationCodeGuard.CODE_LENGTH);
        codeGuard.store(identifier, code);
        return code;
    }

    public void verifyCode(String identifier, String inputCode) {
        codeGuard.verify(identifier, inputCode);
    }

    @Override
    public void sendEmailCode(String email) {
        Filters filters = new Filters().eq(UserAccount::getEmail, email);
        this.generateNumericCode(email);
//        UserAccount userAccount = this.getUserByFilter(filters);
        // TODO: Send email with the code
        // emailService.sendEmail(email, "Verification Code", "Your verification code is: " + code);
    }

    @Override
    public void sendMobileCode(String mobile) {
        Filters filters = new Filters().eq(UserAccount::getMobile, mobile);
//        UserAccount userAccount = this.getUserByFilter(filters);
        // TODO: Send SMS with the code
    }

    @Override
    public UserInfo loginByEmailCode(String email, String code) {
        verifyCode(email, code);
        Optional<UserAccount> optionalUserAccount = accountService.getUserByEmail(email);
        UserInfo userInfo;
        if (optionalUserAccount.isEmpty()) {
            userInfo = accountService.registerNewUser(email, null, null);
        } else {
            userInfo = profileService.getUserInfo(optionalUserAccount.get().getId());
        }
        return userInfo;
    }

    @Override
    public UserInfo loginByMobileCode(String mobile, String code) {
        verifyCode(mobile, code);
        Optional<UserAccount> optionalUserAccount = accountService.getUserByMobile(mobile);
        UserInfo userInfo;
        if (optionalUserAccount.isEmpty()) {
            userInfo = accountService.registerNewUser(null, mobile, null);
        } else {
            userInfo = profileService.getUserInfo(optionalUserAccount.get().getId());
        }
        return userInfo;
    }

    /**
     * User login by email and password
     *
     * @param email    Email address
     * @param password Password
     * @return UserInfo
     */
    @Override
    public UserInfo loginByEmailAndPassword(String email, String password) {
        UserAccount userAccount = accountService.getUserByEmail(email).orElseThrow(
                () -> new BusinessException("User or password is incorrect."));
        // The account still identifies WHO is signing in; only the credential moved. Verifying
        // against the person means someone employed by two companies has one password to remember,
        // and it is the same one whichever account they arrive through.
        //
        // requireProfile's own error ("not linked to a person") must not escape here: this endpoint
        // is anonymous, and a distinct message would tell a stranger which accounts exist but are
        // broken — an account-existence oracle. Inside, it is a data fault; outside, it is just a
        // failed login.
        UserIdentity identity;
        try {
            identity = identityService.requireIdentity(userAccount);
        } catch (BusinessException e) {
            log.error("Password login blocked: account {} has no linked person — run the "
                    + "credentials migration. Reporting a plain failed login to the caller.", userAccount.getId());
            throw new BusinessException("User or password is incorrect.");
        }
        if (!identityService.matchesPassword(identity, password)) {
            throw new BusinessException("User or password is incorrect.");
        }
        return profileService.getUserInfo(userAccount.getId());
    }

    /**
     * Generate a new session ID for a user
     *
     * @param userId User ID
     * @return Session ID
     */
    public String generateSessionId(Long userId) {
        // Tenant + account lifecycle gates — the single choke point every login flow
        // passes through, and it runs AFTER credentials are verified.
        validateTenantActive(userId);
        validateAccountActive(userId);
        String sessionId = UUIDUtils.shortUUID22();
        // Store session ID -> user ID mapping in cache
        String sessionKey = RedisConstant.SESSION + sessionId;
        cacheService.save(sessionKey, userId, RedisConstant.ONE_MONTH);
        return sessionId;
    }

    /**
     * User registration by email and password
     * @param email    email
     * @param password Password
     * @return UserInfo
     */
    @Override
    @Transactional
    public UserInfo registerByEmailAndPassword(String email, String password) {
        // Check if username already exists
        Filters filter = new Filters().eq(UserAccount::getEmail, email);
        if (accountService.count(filter) > 0) {
            throw new BusinessException("Email already exists: " + email);
        }
        return accountService.registerNewUser(email, null, password);
    }

    /**
     * Forgot password — issue a self-service PASSWORD_RESET token and email the set-password link.
     * Delegates to {@link UserInvitationService}; silently no-ops for an unknown email (no
     * account enumeration).
     */
    @Override
    public void forgetPassword(String email) {
        invitationService.forgotPassword(email);
    }

    /**
     * Set the password via a token — serves both invitation-accept and forgot-password reset.
     * Delegates to {@link UserInvitationService} (validates the token, sets a fresh salted hash,
     * and activates an INVITED account).
     */
    @Override
    @Transactional
    public void resetPassword(String token, String newPassword) {
        invitationService.acceptToken(token, newPassword);
    }

    /** Validate a token for the public set-password page. */
    @Override
    public InvitationInfo inviteInfo(String token) {
        return invitationService.inspectToken(token);
    }

}