package io.softa.starter.user.service.impl;

import org.apache.commons.lang3.StringUtils;

import java.util.Comparator;
import java.util.List;
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
import io.softa.starter.user.dto.AuthenticationResult;
import io.softa.starter.user.dto.JoinContacts;
import io.softa.starter.user.dto.JoinVerification;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.dto.MembershipOption;
import io.softa.starter.user.exception.MultipleMembershipsException;
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
    public AuthenticationResult authenticateByCode(String identifier, String code) {
        verifyCode(identifier, code);
        // Resolved by LOGIN IDENTIFIER on the person, not by a company's work contact: the code
        // was sent to an identifier, and that identifier is what identifies the human being.
        return this.afterAuthentication(this.resolveIdentity(identifier,
                "This account is not linked to any company. Please contact your HR."));
    }

    @Override
    public AuthenticationResult authenticateByPassword(String identifier, String password) {
        // Same message for "no such identifier" and "wrong password": splitting them turns the
        // login form into an account-existence oracle.
        UserIdentity identity = this.resolveIdentity(identifier, "Incorrect account or password.");
        if (!identityService.matchesPassword(identity, password)) {
            throw new BusinessException("Incorrect account or password.");
        }
        return this.afterAuthentication(identity);
    }

    /**
     * Find the PERSON behind a login identifier, healing the identifier as it goes.
     *
     * <p>Identifiers live on {@code UserIdentity} and are seeded when a person is created — but
     * only since that seeding existed. Rows created before it have none, so resolving by
     * identifier alone would lock every one of those people out of their own account. The fallback
     * resolves the way login used to (by the ACCOUNT's work contact) and writes the identifier
     * back, so the data converges on its own as people sign in: no migration script, no downtime,
     * and no release where a subset of users simply cannot get in.
     *
     * <p>The fallback is sound only while {@code UserAccount.email} is globally unique — that is
     * what makes "one account per address" true. Narrowing that index to {@code (tenantId, email)}
     * must therefore wait for a LATER release, once this backfill has converged; doing both at
     * once would make the fallback ambiguous exactly where it is still needed.
     *
     * @param notFoundMessage what the caller may safely tell an anonymous stranger. Both reasons
     *                        ("no such identifier", "identifier is not
     *                        linked to a person") must report the same thing: a distinct message would confirm which accounts exist.
     */
    private UserIdentity resolveIdentity(String identifier, String notFoundMessage) {
        Optional<UserIdentity> byIdentifier = identityService.findByLoginIdentifier(identifier);
        if (byIdentifier.isPresent()) {
            return byIdentifier.get();
        }
        UserAccount account = accountService.getUserByEmail(identifier)
                .or(() -> accountService.getUserByMobile(identifier))
                .orElseThrow(() -> new BusinessException(notFoundMessage));
        UserIdentity identity;
        try {
            identity = identityService.requireIdentity(account);
        } catch (BusinessException e) {
            // A data fault inside, an ordinary failed login outside — see notFoundMessage.
            log.error("Login blocked: account {} has no linked person; the credentials migration "
                    + "did not cover it. Reporting a plain failure to the caller.", account.getId());
            throw new BusinessException(notFoundMessage);
        }
        identityService.adoptIdentifier(identity, identifier);
        return identity;
    }

    /**
     * The shared tail of every authentication path: decide what the client must do next.
     *
     * <p>One place rather than per-path, because the three questions (needs a password? one
     * company or a choice? which membership?) have the same answers however the person proved
     * who they are — and a path that skipped one of them would be a hole rather than a variation.
     */
    private AuthenticationResult afterAuthentication(UserIdentity identity) {
        Long profileId = identity.getProfileId();
        boolean mustSetPassword = StringUtils.isBlank(identity.getPassword());
        List<MembershipOption> options = this.listCompanies(profileId);
        List<MembershipOption> enterable = options.stream()
                .filter(MembershipOption::selectable).toList();
        if (options.size() == 1 && enterable.size() == 1) {
            return AuthenticationResult.resolved(profileId,
                    profileService.getUserInfo(enterable.get(0).accountId()), mustSetPassword);
        }
        if (options.isEmpty()) {
            throw new BusinessException("Your account is not linked to any company. Please contact your HR.");
        }
        return AuthenticationResult.choicePending(profileId, options, mustSetPassword);
    }


    @Override
    public AuthenticationResult afterJoin(Long profileId) {
        // The identity, not the profile: what decides the next step is whether a password exists,
        // and that lives on the credential.
        return identityService.findByProfile(profileId)
                .map(this::afterAuthentication)
                .orElseThrow(() -> new BusinessException("Person record not found."));
    }

    @Override
    public void sendJoinCode(String rawToken, String channel) {
        // The address never crosses the wire in either direction: the caller sends a token, the
        // invitation service resolves it, and the code goes out to what IT stored.
        String address = invitationService.resolveJoinChannel(rawToken, channel);
        if ("mobile".equalsIgnoreCase(channel)) {
            this.sendMobileCode(address);
        } else {
            this.sendEmailCode(address);
        }
    }

    @Override
    @Transactional
    public JoinVerification verifyJoinCode(String rawToken, String channel, String code) {
        // Resolving the address from the token (not from the caller) is what keeps this from being
        // a way to verify a code against an address of the caller's choosing.
        String address = invitationService.resolveJoinChannel(rawToken, channel);
        this.verifyCode(address, code);

        // Find-or-create by the address the invitation was sent to. Found = this person already
        // works somewhere and is being added to a second company, and they keep ONE person record
        // (that is the whole point of the global profile). Not found = their first company.
        Optional<UserIdentity> existing = identityService.findByLoginIdentifier(address);
        if (existing.isPresent()) {
            return new JoinVerification(existing.get().getProfileId(),
                    StringUtils.isBlank(existing.get().getPassword()));
        }
        // Brand new person: no password by construction, so the password step always follows.
        // Constructed through the profile service, which is the one waived choke point where a
        // person and their credentials row are minted together.
        return new JoinVerification(profileService.createPersonForJoin(address), true);
    }

    @Override
    @Transactional
    public void setJoinPassword(String rawToken, Long profileId, String newPassword) {
        JoinContacts contacts = invitationService.resolveJoinContacts(rawToken);
        UserIdentity identity = identityService.findByProfile(profileId)
                .orElseThrow(() -> new BusinessException("Person record not found."));

        // Both checks matter. The first ties the person to THIS invitation, so holding a link
        // cannot reach an unrelated person. The second keeps it a first-password path rather than
        // a reset — someone who already has a password must prove it, or arrive by code.
        if (!contacts.includes(identity.getLoginEmail()) && !contacts.includes(identity.getLoginMobile())) {
            throw new BusinessException("This link does not belong to that account.");
        }
        if (StringUtils.isNotBlank(identity.getPassword())) {
            throw new BusinessException("A password is already set — sign in with it instead.");
        }
        // Strength rules live inside setPassword, checked against this person's own contacts.
        identityService.setPassword(identity.getId(), newPassword);
    }

    @Override
    public boolean mustSetPassword(Long profileId) {
        return identityService.findByProfile(profileId)
                .map(identity -> StringUtils.isBlank(identity.getPassword()))
                // Unknown person → do not claim they are fine; the caller fails elsewhere.
                .orElse(Boolean.FALSE);
    }
    @Override
    public List<MembershipOption> listCompanies(Long profileId) {
        return accountService.listMembershipsOf(profileId).stream()
                .map(account -> new MembershipOption(
                        account.getId(), account.getTenantId(),
                        tenantInfoService == null ? null
                                : tenantInfoService.getTenantName(account.getTenantId()),
                        account.getStatus()))
                // Selectable first: the common case is one usable company among some frozen ones,
                // and making the person hunt for it in a mixed list is a needless step.
                .sorted(Comparator.comparing(MembershipOption::selectable).reversed())
                .toList();
    }

    @Override
    public Long resolveSingleMembership(Long profileId) {
        List<MembershipOption> options = this.listCompanies(profileId);
        if (options.isEmpty()) {
            // Authenticated, but a member of nothing — the account was off-boarded everywhere, or
            // a profile exists with no membership yet. Either way there is nowhere to go.
            throw new BusinessException("Your account is not linked to any company. Please contact your HR.");
        }
        List<MembershipOption> selectable = options.stream().filter(MembershipOption::selectable).toList();
        if (selectable.size() == 1 && options.size() == 1) {
            return selectable.get(0).accountId();
        }
        // More than one, or exactly one that is not enterable: both need the picker. Refusing here
        // rather than auto-picking is deliberate — silently choosing for someone who belongs to two
        // companies puts them in a workspace they did not ask for, and the wrong one is worse than
        // an extra click.
        throw new MultipleMembershipsException(options);
    }

    @Override
    public Long selectCompany(Long profileId, Long accountId) {
        MembershipOption chosen = this.listCompanies(profileId).stream()
                .filter(option -> option.accountId().equals(accountId))
                .findFirst()
                // Ownership check, not a convenience: without it an authenticated person could name
                // any accountId and be issued a session in a company they are not a member of.
                .orElseThrow(() -> new BusinessException("That company is not available for your account."));
        if (!chosen.selectable()) {
            throw new BusinessException(accountDeniedMessage(chosen.status()));
        }
        return accountId;
    }

    /**
     * Generate a new session ID for a user
     *
     * @param userId User ID
     * @return Session ID
     */
    @Override
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