package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.RandomUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.base.message.MailRequestMessage;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.enums.InvitationPurpose;
import io.softa.starter.user.enums.InvitationStatus;
import io.softa.starter.user.entity.UserInvitation;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserInvitationService;

/**
 * {@link UserInvitationService} — token issuance + acceptance. Runs under {@link SkipPermissionCheck}
 * (called from tenant/system contexts and from the public set-password endpoint). The raw token is
 * only ever emailed; the DB stores its SHA-256 hash. See {@link UserInvitation} for the model notes.
 */
@Slf4j
@Service
public class UserInvitationServiceImpl extends EntityServiceImpl<UserInvitation, Long>
        implements UserInvitationService {

    /** Invitation / reset links are valid for 7 days. */
    private static final int EXPIRY_DAYS = 7;
    /** Token entropy: 32 random bytes → URL-safe Base64 (~43 chars). */
    private static final int TOKEN_BYTES = 32;
    /** Minimum password length enforced server-side (FE mirrors it). */
    private static final int MIN_PASSWORD_LENGTH = 8;
    /** MailTemplate codes — seeded as system ({@code tenantId=0}) templates in message-starter. */
    private static final String TEMPLATE_INVITE = "user.invitation";
    private static final String TEMPLATE_RESET = "user.password-reset";

    private final UserAccountService accountService;
    private final ApplicationEventPublisher eventPublisher;
    private final String frontendBaseUrl;

    public UserInvitationServiceImpl(UserAccountService accountService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    @SkipPermissionCheck
    @Override
    @Transactional
    public void invite(Long userId, Long invitedBy) {
        Assert.notNull(userId, "userId is required");
        UserAccount account = accountService.getById(userId)
                .orElseThrow(() -> new BusinessException("User not found."));
        Assert.notBlank(account.getEmail(), "This user has no email to send the invitation to.");
        // Create→invite is decoupled: creating a user no longer emails — this explicit Invite does.
        // A never-activated account (no password yet) is marked INVITED here so the set-password link
        // activates it (acceptToken flips INVITED→ACTIVE). An account that already has a password
        // (ACTIVE / LOCKED / …) is left untouched — re-inviting it just re-sends the link.
        if (StringUtils.isBlank(account.getPassword())) {
            account.setStatus(AccountStatus.INVITED);
            accountService.updateOne(account);
        }
        issue(account, InvitationPurpose.INVITE, invitedBy);
    }

    @SkipPermissionCheck
    @Override
    @Transactional
    public void forgotPassword(String email) {
        if (StringUtils.isBlank(email)) {
            return;
        }
        Optional<UserAccount> account = accountService.getUserByEmail(email);
        if (account.isEmpty()) {
            // Do not reveal whether the email is registered.
            log.info("forgotPassword for an unknown email — ignored (no enumeration).");
            return;
        }
        issue(account.get(), InvitationPurpose.PASSWORD_RESET, null);
    }

    /**
     * Revoke prior PENDING tokens for the user, issue a fresh one, and email the link.
     *
     * <p>{@link UserInvitation} is multiTenant, so the ORM auto-stamps {@code tenant_id} from the CURRENT
     * request context — with {@code enableMultiTenancy=true}, {@code tenant_id} is readonly and CANNOT be
     * set explicitly. {@link #invite} runs under the target tenant's context (the provisioning
     * orchestrator's {@code inTenantContext(newTenantId)}), so the stamp is correct there.
     *
     * <p><b>Do NOT source the tenant from {@code account.getTenantId()}</b>: UserAccount is non-multiTenant,
     * so its own {@code tenant_id} is readonly-dropped on insert (always null) — reading it and pinning the
     * context to it would stamp the invitation null. The public {@link #forgotPassword} path has no tenant
     * context, so its reset rows carry a null {@code tenant_id} (they are looked up cross-tenant by token,
     * so a null only affects the authed 明细 scoping — a super-admin still sees them; a tenant-admin does not).
     */
    private void issue(UserAccount account, InvitationPurpose purpose, Long invitedBy) {
        revokePending(account.getId());

        String rawToken = RandomUtils.randomString(TOKEN_BYTES);
        UserInvitation invitation = new UserInvitation();
        invitation.setUserId(account.getId());
        invitation.setEmail(account.getEmail());
        invitation.setPurpose(purpose);
        invitation.setTokenHash(EncryptUtils.computeSha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(invitedBy);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS));
        // "sent" here = requested; the actual delivery + status is message-starter's (MailSendRecord).
        invitation.setSentAt(LocalDateTime.now());
        this.createOne(invitation);

        // Request the set-password / reset email. A framework MailRequestedEvent that message-starter
        // renders (its MailTemplate) + delivers (its outbox/MQ) — no message-starter dependency here
        // (user-starter ⊥ message-starter). The listener runs AFTER_COMMIT, so the mail only goes out
        // once this invitation has committed; if no message-starter is present it is a graceful no-op.
        String link = frontendBaseUrl.replaceAll("/+$", "") + "/set-password?token=" + rawToken;
        String template = purpose == InvitationPurpose.PASSWORD_RESET ? TEMPLATE_RESET : TEMPLATE_INVITE;
        eventPublisher.publishEvent(new MailRequestMessage(
                List.of(account.getEmail()), template, Map.of("link", link, "expiryDays", EXPIRY_DAYS)));
        // Dev aid: surface the set-password / reset link so it can be copied from the logs when SMTP / MQ
        // is not wired locally. ⚠️ The link carries a one-time credential token — lower this to debug or
        // remove it before production so the token is not leaked into prod logs.
        log.debug("Invitation link ({}) for {}: {}", purpose, account.getEmail(), link);
    }

    private void revokePending(Long userId) {
        // A user has at most a handful of invitations; filter PENDING in memory to avoid an
        // enum-valued query filter.
        List<UserInvitation> existing = this.searchList(new Filters().eq(UserInvitation::getUserId, userId));
        for (UserInvitation invitation : existing) {
            if (invitation.getStatus() == InvitationStatus.PENDING) {
                invitation.setStatus(InvitationStatus.REVOKED);
                this.updateOne(invitation);
            }
        }
    }

    // @CrossTenant: public set-password endpoint has NO tenant context; look the (multiTenant) row up
    // by tokenHash across all tenants. The token hash is the global unique key.
    @SkipPermissionCheck
    @CrossTenant
    @Override
    @Transactional
    public void acceptToken(String rawToken, String newPassword) {
        Assert.notBlank(rawToken, "This link is invalid.");
        Assert.notBlank(newPassword, "New password cannot be empty.");
        Assert.isTrue(newPassword.trim().length() >= MIN_PASSWORD_LENGTH,
                "Password must be at least " + MIN_PASSWORD_LENGTH + " characters.");

        UserInvitation invitation = this.searchOne(
                        new Filters().eq(UserInvitation::getTokenHash, EncryptUtils.computeSha256(rawToken)))
                .orElseThrow(() -> new BusinessException("This link is invalid."));

        if (invitation.getStatus() != InvitationStatus.PENDING) {
            throw new BusinessException("This link has already been used or is no longer valid.");
        }
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            invitation.setStatus(InvitationStatus.EXPIRED);
            this.updateOne(invitation);
            throw new BusinessException("This link has expired. Please request a new one.");
        }

        UserAccount account = accountService.getById(invitation.getUserId())
                .orElseThrow(() -> new BusinessException("Account not found."));
        String salt = PasswordUtils.generateSalt();
        account.setPasswordSalt(salt);
        account.setPassword(PasswordUtils.hashPassword(newPassword, salt));
        if (account.getStatus() == AccountStatus.INVITED) {
            account.setStatus(AccountStatus.ACTIVE);
            account.setActivationTime(LocalDateTime.now());
        }
        accountService.updateOne(account);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        this.updateOne(invitation);

        log.info("User {} set password via {} token (invitation {}).",
                invitation.getUserId(), invitation.getPurpose(), invitation.getId());
    }

    // @CrossTenant: public token-inspection endpoint has no tenant context — see acceptToken.
    @SkipPermissionCheck
    @CrossTenant
    @Override
    public InvitationInfo inspectToken(String rawToken) {
        if (StringUtils.isBlank(rawToken)) {
            return InvitationInfo.invalid();
        }
        Optional<UserInvitation> found = this.searchOne(
                new Filters().eq(UserInvitation::getTokenHash, EncryptUtils.computeSha256(rawToken)));
        if (found.isEmpty()) {
            return InvitationInfo.invalid();
        }
        UserInvitation invitation = found.get();
        boolean usable = invitation.getStatus() == InvitationStatus.PENDING
                && (invitation.getExpiresAt() == null || invitation.getExpiresAt().isAfter(LocalDateTime.now()));
        return usable ? InvitationInfo.valid(invitation.getEmail(), invitation.getPurpose())
                      : InvitationInfo.invalid();
    }
}
