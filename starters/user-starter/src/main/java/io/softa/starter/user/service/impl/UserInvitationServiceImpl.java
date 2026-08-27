package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.ContextUtils;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailRequestMessage;
import io.softa.framework.base.message.MessageScope;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.RandomUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.base.message.SmsRequestMessage;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.dto.InvitationInfo;
import io.softa.starter.user.dto.JoinContacts;
import io.softa.starter.user.dto.JoinEntry;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserInvitation;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.enums.InvitationPurpose;
import io.softa.starter.user.enums.InvitationStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
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
    /** Cap on the unbind reason (W5). Long enough for a sentence, short enough to stay readable. */
    private static final int MAX_REASON_LENGTH = 500;
    /** Template codes, seeded as system ({@code tenantId=0}) rows by the host app.
     *  {@code MailTemplate} and {@code SmsTemplate} are separate models, so one code names
     *  the invitation in BOTH channels — same message, different transport. */
    private static final String TEMPLATE_INVITE = "user.invitation";
    /** Mail only. A password reset starts from the login screen by typing an email, so the
     *  address is known and an SMS counterpart would add cost without reach. */
    private static final String TEMPLATE_RESET = "user.password-reset";

    private final UserAccountService accountService;
    private final UserIdentityService identityService;
    private final ApplicationEventPublisher eventPublisher;
    /** Optional: the join screens show the inviting company's name; absent tenant-starter → null. */
    private final TenantInfoService tenantInfoService;
    private final String frontendBaseUrl;

    public UserInvitationServiceImpl(UserAccountService accountService,
                                     UserIdentityService identityService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Autowired(required = false) TenantInfoService tenantInfoService,
                                     @Value("${app.frontend-base-url:http://localhost:3000}") String frontendBaseUrl) {
        this.accountService = accountService;
        this.identityService = identityService;
        this.eventPublisher = eventPublisher;
        this.tenantInfoService = tenantInfoService;
        this.frontendBaseUrl = frontendBaseUrl;
    }

    /**
     * Advance the account's status for an outgoing invitation, returning whether it changed
     * (so the caller only writes when there is something to write).
     *
     * <p>Create→invite is decoupled: creating a user contacts nobody — this explicit Invite
     * does, and it is what advances {@code PENDING → INVITED}. {@code acceptToken} then flips
     * {@code INVITED → ACTIVE}, giving the three-state axis the account list renders.
     *
     * <p>The gate is "has no password yet", not "is PENDING", for two reasons:
     * an account already sitting on {@code INVITED} must stay invitable (re-sending the link
     * is the normal remedy for a lost or expired mail) yet needs no write; and one that
     * already has a password ({@code ACTIVE} / {@code LOCKED} / …) is left untouched —
     * re-inviting must never demote a working account.
     *
     * <p>{@code hasPassword} arrives as a parameter rather than being read off the account:
     * credentials live on {@link io.softa.starter.user.entity.UserIdentity}, resolved through
     * the person, and this method stays static so the axis is testable without wiring beans.
     */
    static boolean applyInviteTransition(UserAccount account, boolean hasPassword) {
        if (hasPassword || account.getStatus() == AccountStatus.INVITED) {
            return false;
        }
        account.setStatus(AccountStatus.INVITED);
        return true;
    }

    @SkipPermissionCheck
    @Override
    @Transactional
    public void invite(Long userId, Long invitedBy) {
        Assert.notNull(userId, "userId is required");
        UserAccount account = accountService.getById(userId)
                .orElseThrow(() -> new BusinessException("User not found."));
        // At least ONE channel must be maintained — the invitation goes to every channel the
        // account has (work mobile SMS + work email), so requiring the email specifically would
        // block an account reachable only by phone. Refusing here (rather than issuing a token
        // nobody receives) is what keeps the account's status honest: it stays PENDING, so the
        // list still reads "not contacted" instead of claiming an invitation went out.
        if (StringUtils.isBlank(account.getEmail()) && StringUtils.isBlank(account.getMobile())) {
            throw new BusinessException(
                    "This user has no work email or work mobile — add a contact method before inviting.");
        }
        // Create→invite is decoupled: creating a user contacts nobody — this explicit Invite does,
        // and it is what advances PENDING → INVITED. "Has this person set a password yet?" — read
        // from the person's credentials when the account is linked. An account with no profile link
        // is broken legacy/partial data (it already cannot log in); treat it as password-less so an
        // invite still marks it INVITED and sends the set-password link, rather than throwing
        // requireIdentity's "not linked to a person" and dead-ending the one operation that could
        // recover it.
        boolean hasPassword = account.getProfileId() != null
                && StringUtils.isNotBlank(identityService.requireIdentity(account).getPassword());
        if (applyInviteTransition(account, hasPassword)) {
            accountService.updateOne(account);
        }
        issue(account, InvitationPurpose.INVITE, invitedBy);
    }

    /** Statuses a mis-binding can be corrected from (§2.3 matrix, row "Unbind & Re-invite"). */
    private static final Set<AccountStatus> UNBINDABLE = Set.of(
            AccountStatus.INVITED, AccountStatus.ACTIVE, AccountStatus.LOCKED, AccountStatus.FROZEN);

    @SkipPermissionCheck
    @CrossTenant
    @Override
    @Transactional
    public void unbindAndReinvite(Long userId, String newEmail, String newMobile, String reason,
            Long operatedBy) {
        Assert.notNull(userId, "userId is required");
        Assert.notBlank(reason, "A reason is required to unbind this account.");
        Assert.isTrue(reason.trim().length() <= MAX_REASON_LENGTH,
                "The reason cannot exceed " + MAX_REASON_LENGTH + " characters.");
        String email = StringUtils.trimToNull(newEmail);
        String mobile = StringUtils.trimToNull(newMobile);
        if (email == null && mobile == null) {
            throw new BusinessException(
                    "Enter the correct work email or work mobile before re-inviting.");
        }

        UserAccount account = accountService.getById(userId)
                .orElseThrow(() -> new BusinessException("User not found."));
        // PENDING is excluded on purpose: nobody has accepted, so there is nothing to unbind —
        // correct the contact and Send. DEACTIVATED is excluded because the membership is closed;
        // bringing it back is reviveMembership, not a re-invitation.
        if (!UNBINDABLE.contains(account.getStatus())) {
            throw new BusinessException("This account cannot be unbound in its current state.");
        }
        // Refused here rather than at the unique index: "this email is already registered" from a
        // constraint violation names no account and arrives after the unbind has been written.
        accountService.getUserByEmail(email).filter(other -> !other.getId().equals(userId))
                .ifPresent(other -> {
                    throw new BusinessException(
                            "That work email already belongs to another account.");
                });

        // ① Release what this company issued from the OLD person's login identifiers, FIRST. The
        // order encodes which half is security-critical: a failure after this point leaves the
        // membership detached, which is recoverable, while the reverse leaves the wrong person
        // holding a live login route into an address this company is about to hand to someone else.
        accountService.releaseLoginIdentifiers(account);

        // ② Detach, ③ record the corrected contacts, and reset to "not contacted yet".
        Long previousProfileId = account.getProfileId();
        account.setProfileId(null);
        account.setActivationTime(null);
        account.setEmail(email);
        account.setMobile(mobile);
        account.setStatus(AccountStatus.INVITED);
        // updateOne(entity, false): detaching means WRITING nulls, which the default overload
        // drops — it would leave the membership attached to the wrong person and report success.
        accountService.updateOne(account, false);

        // ④ Fresh token, every outstanding one revoked inside issue().
        issue(account, InvitationPurpose.REINVITE, operatedBy, reason.trim());
        log.warn("Account {} unbound from profile {} and re-invited by {}. Reason: {}",
                userId, previousProfileId, operatedBy, reason.trim());
    }

    @SkipPermissionCheck
    @Override
    @Transactional
    public void revokeInvitation(Long userId) {
        Assert.notNull(userId, "userId is required");
        UserAccount account = accountService.getById(userId)
                .orElseThrow(() -> new BusinessException("User not found."));
        // Credentials live on the person; an account with no profile link cannot have joined, so
        // treat it as password-less — revoke stays applicable rather than throwing on broken data.
        boolean hasPassword = account.getProfileId() != null
                && StringUtils.isNotBlank(identityService.requireIdentity(account).getPassword());
        boolean statusChanged = applyRevokeTransition(account, hasPassword);
        // Kill the live link regardless of the status write: revoking an account already sitting
        // on PENDING (a stray token from an earlier cycle) still has to invalidate that token —
        // gating the revoke on the status change would leave it working.
        revokePending(userId);
        if (statusChanged) {
            accountService.updateOne(account);
        }
        log.info("Invitation revoked for user {} — link invalidated, account back to PENDING.", userId);
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
    /**
     * Move the account back for a withdrawn invitation, returning whether it changed.
     *
     * <p>Refuses outright once the account has a password: the person has already joined, and
     * "revoking" them would strand a working account in a pre-activation state nothing can move
     * forward. Ending an existing membership is off-boarding — a different action with different
     * obligations (release the work-email binding).
     *
     * <p>{@code hasPassword} is passed in rather than read off the account: credentials live on the
     * person's {@link io.softa.starter.user.entity.UserIdentity} now, and keeping this static leaves
     * the axis unit-testable without wiring beans.
     *
     * @throws BusinessException when the account has already been activated
     */
    static boolean applyRevokeTransition(UserAccount account, boolean hasPassword) {
        if (hasPassword) {
            throw new BusinessException("This user has already joined — revoke does not apply.");
        }
        if (account.getStatus() != AccountStatus.INVITED) {
            return false;
        }
        account.setStatus(AccountStatus.PENDING);
        return true;
    }

    /**     * Revoke prior PENDING tokens for the user, issue a fresh one, and email the link.
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
        this.issue(account, purpose, invitedBy, null);
    }

    private void issue(UserAccount account, InvitationPurpose purpose, Long invitedBy, String reason) {
        revokePending(account.getId());

        // Who this invitation BELONGS to: the invitee's own tenant, never the caller's. It is a record
        // of that person's account at that company, so it is that company's row — following whoever
        // happened to click files it under a different company entirely whenever an operator can see
        // across tenants. Provisioning agrees anyway: it creates the account inside
        // inTenantContext(newTenantId) before inviting. Ambient is only the fallback for an account
        // carrying no tenant of its own.
        //
        // Deliberately NOT the same value as the render tier chosen further down. Ownership and tier
        // answer different questions, and on the public forgotPassword path they legitimately part
        // ways: the row still belongs to the account's tenant, while the mail renders platform-tier
        // because there is no context to trust and no cross-tier template fallback to catch a miss.
        Long owningTenant = account.getTenantId() != null
                ? account.getTenantId()
                : ContextHolder.getContext().getTenantId();

        String rawToken = RandomUtils.randomString(TOKEN_BYTES);
        UserInvitation invitation = new UserInvitation();
        invitation.setUserId(account.getId());
        // Both identifiers are recorded, so the row answers "which channels was this addressed
        // to?" — which the email alone could not once delivery fanned out. The delivery OUTCOME
        // (sent / bounced / retried) stays in message-starter's send records; duplicating it here
        // would give two answers that drift.
        invitation.setEmail(account.getEmail());
        invitation.setMobile(account.getMobile());
        invitation.setPurpose(purpose);
        invitation.setTokenHash(EncryptUtils.computeSha256(rawToken));
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setInvitedBy(invitedBy);
        invitation.setReason(reason);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(EXPIRY_DAYS));
        // "sent" here = requested; the actual delivery + status is message-starter's (MailSendRecord).
        invitation.setSentAt(LocalDateTime.now());
        // Pinned rather than left to the ambient stamp. UserInvitation is multiTenant, and the ORM skips
        // tenant stamping entirely while crossTenant is set — which UserAccountController.invite sets for
        // a platform super-admin so it can reach a roster spanning tenants. The read needed that window;
        // the write inherited it, and every invitation an operator sent landed with a null tenant_id.
        // Those rows are invisible to the tenant whose member they are about: their User Invitations page
        // filters on tenant_id, and null matches nothing — so the one case where someone acts on a
        // tenant's behalf is the one case that left them no record of it.
        //
        // inTenantContext clears crossTenant and pins the id; createdId / createdBy survive because they
        // come from Context.userId / Context.name, which it carries over.
        ContextUtils.inTenantContext(owningTenant, () -> this.createOne(invitation));

        // Request the set-password / reset email. A framework MailRequestedEvent that message-starter
        // renders (its MailTemplate) + delivers (its outbox/MQ) — no message-starter dependency here
        // (user-starter ⊥ message-starter). The listener runs AFTER_COMMIT, so the mail only goes out
        // once this invitation has committed; if no message-starter is present it is a graceful no-op.
        // The two purposes land on DIFFERENT pages, and must. A password reset is exactly one
        // action — set a credential — while an invitation has to check the link, verify identity,
        // set a password if the person is new, and then confirm joining, because that confirmation
        // is what binds them to the company. Sending an invitation to /set-password (as this did)
        // sets a password and leaves the membership INVITED forever: the person appears to have
        // completed the flow and still cannot get in.
        String page = purpose == InvitationPurpose.PASSWORD_RESET ? "/set-password" : "/join";
        String link = frontendBaseUrl.replaceAll("/+$", "") + page + "?token=" + rawToken;
        // Deliver to EVERY channel the account has, not just the email: an employee reachable
        // only by work mobile is a normal case, and one who has both should not depend on which
        // inbox they check first. Both carry the SAME link — it is one invitation, so accepting
        // from either channel consumes the same token.
        //
        // Tier of the render: with a tenant context (invite / authed reset) the tenant's own template
        // + wording; the public forgotPassword path has no tenant context, so it renders the
        // platform-tier template — the platform row doubles as the copy source for tenants AND the
        // platform's own sender.
        //
        // WHICH tenant, though, is the account's — not the ambient one. They differ exactly when an
        // operator who can see across tenants clicks Invite: the ambient context is then the
        // OPERATOR's company, and sourcing the tier from it renders another company's template and
        // routes through another company's mail server for a mail about this person's account at
        // theirs. On every other path the two already agree, since provisioning creates the account
        // inside inTenantContext(newTenantId) before inviting.
        //
        // The no-context path deliberately stays platform-tier rather than reaching for the
        // account's tenant: template resolution has no fallback across tiers any more, so a tenant
        // whose copy of this template is missing or disabled would get an exception where the
        // platform row always resolves.
        Long tenantId = ContextHolder.getContext().getTenantId() != null ? account.getTenantId() : null;
        MessageScope scope = tenantId != null ? MessageScope.TENANT : MessageScope.PLATFORM;
        if (StringUtils.isNotBlank(account.getEmail())) {
            String template = purpose == InvitationPurpose.PASSWORD_RESET ? TEMPLATE_RESET : TEMPLATE_INVITE;
            eventPublisher.publishEvent(new MailRequestMessage(
                    List.of(account.getEmail()), template, Map.of("link", link, "expiryDays", EXPIRY_DAYS),
                    tenantId, scope));
        }
        // Invitations only. A password reset is started from the login screen by typing an email,
        // so the address is known and SMS adds nothing but cost.
        //
        // SmsRequestMessage carries no tenant/scope tier of its own — the SMS consumer resolves the
        // sender from the request context — so the tier above applies to the mail render only.
        if (purpose != InvitationPurpose.PASSWORD_RESET && StringUtils.isNotBlank(account.getMobile())) {
            eventPublisher.publishEvent(new SmsRequestMessage(
                    List.of(account.getMobile()), TEMPLATE_INVITE,
                    Map.of("link", link, "expiryDays", EXPIRY_DAYS)));
        }
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
        // Strength is checked inside credentialService.setPassword against the person's own
        // identifiers (PRD D4) — a bare minimum-length assertion here would contradict it.

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
// The password goes on the PERSON, so accepting an invitation from company B when you
        // already work at company A replaces one global credential rather than minting a second.
        identityService.setPassword(account, newPassword);
        // PENDING is accepted alongside INVITED defensively: today every token comes from
        // invite() (which has already flipped to INVITED) or forgotPassword() (the account
        // has a password and stays as it is), so PENDING-with-a-token is unreachable. Were a
        // future path to issue a token without flipping, the account would otherwise receive
        // a password and stay unable to log in — a silent lockout that looks like a bad
        // password rather than a wrong status.
        if (account.getStatus() == AccountStatus.INVITED || account.getStatus() == AccountStatus.PENDING) {
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

    /**
     * Confirm joining: bind the person to the membership and activate it (PRD §3.3 / §4.4).
     *
     * <p><b>Binding happens HERE, not when the password was set.</b> Someone who sets a password
     * and closes the tab has proved control of the invitation but has not agreed to join this
     * company — their membership stays INVITED and the confirm screen is what they return to
     * (E8). Activating early would make "set a password" mean "accepted", which is exactly the
     * mis-binding the confirm screen exists to prevent.
     *
     * <p>Re-entrant on purpose: a double-tapped Join Now must not fail. An already-activated
     * membership returns quietly rather than throwing, because from the person's point of view
     * they did join.
     *
     * @param rawToken  the invitation token, re-validated here — the caller may have held the
     *                  page open long enough for a revoke or re-send to land
     * @param profileId the person who verified their identity in the preceding step
     */
    @SkipPermissionCheck
    @CrossTenant
    @Override
    @Transactional
    public void confirmJoin(String rawToken, Long profileId) {
        Assert.notNull(profileId, "profileId is required");
        JoinEntry entry = this.inspectJoinToken(rawToken);
        if (!entry.usable()) {
            if (entry.reason() == JoinEntry.Reason.ALREADY_JOINED) {
                return;   // re-entrant: a double tap is not an error
            }
            throw new BusinessException("This link is no longer valid. Please contact your HR.");
        }
        UserInvitation invitation = this.searchOne(new Filters()
                        .eq(UserInvitation::getTokenHash, EncryptUtils.computeSha256(rawToken)))
                .orElseThrow(() -> new BusinessException("This link is invalid."));
        UserAccount account = accountService.getById(invitation.getUserId())
                .orElseThrow(() -> new BusinessException("Account not found."));

        // Refuse if this person already holds a membership of this company (PRD §4.5 duplicate
        // invitation). The unique index would refuse it too, but a clear message beats a
        // constraint violation for something a person can actually be told about.
        boolean alreadyMember = accountService.listMembershipsOf(profileId).stream()
                .anyMatch(existing -> !existing.getId().equals(account.getId())
                        && existing.getTenantId().equals(account.getTenantId()));
        if (alreadyMember) {
            throw new BusinessException(
                    "You are already a member of this company. Please contact your HR.");
        }

        account.setProfileId(profileId);
        account.setStatus(AccountStatus.ACTIVE);
        account.setActivationTime(LocalDateTime.now());
        accountService.updateOne(account);

        invitation.setStatus(InvitationStatus.ACCEPTED);
        invitation.setAcceptedAt(LocalDateTime.now());
        this.updateOne(invitation);
        log.info("Profile {} joined tenant {} via invitation {}.",
                profileId, account.getTenantId(), invitation.getId());
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public JoinContacts resolveJoinContacts(String rawToken) {
        // Re-runs the full five-check gate rather than trusting the page's earlier call: the
        // invitation may have been revoked or re-sent since the page loaded.
        if (!this.inspectJoinToken(rawToken).usable()) {
            throw new BusinessException("This invitation link is no longer usable.");
        }
        UserInvitation invitation = this.searchOne(new Filters()
                        .eq(UserInvitation::getTokenHash, EncryptUtils.computeSha256(rawToken)))
                .orElseThrow(() -> new BusinessException("This invitation link is no longer usable."));
        return new JoinContacts(invitation.getEmail(), invitation.getMobile());
    }

    /**
     * The /join entry check (PRD §3.0) — five conditions, in this order.
     *
     * <p><b>The order is the requirement, not an implementation detail.</b> A token that was
     * superseded by a re-send is "invalid" even if the account is also already active; reporting
     * the account state first would tell the person to contact HR when the real remedy is "open
     * the newest link". Checking cheapest-and-most-specific first also keeps a stranger holding a
     * leaked token from learning anything about the account behind it.
     *
     * <p>The first four all render as "this link has expired" on screen; the reason travels anyway
     * because it is what support needs to decide between re-send, re-invite, and "you already
     * joined".
     */
    @SkipPermissionCheck
    @CrossTenant
    @Override
    public JoinEntry inspectJoinToken(String rawToken) {
        if (StringUtils.isBlank(rawToken)) {
            return JoinEntry.rejected(JoinEntry.Reason.LINK_INVALID);
        }
        Optional<UserInvitation> found = this.searchOne(new Filters()
                .eq(UserInvitation::getTokenHash, EncryptUtils.computeSha256(rawToken)));
        // ① unknown token, or superseded by a re-send / revoke (those set status away from PENDING)
        if (found.isEmpty() || found.get().getStatus() != InvitationStatus.PENDING) {
            return JoinEntry.rejected(JoinEntry.Reason.LINK_INVALID);
        }
        UserInvitation invitation = found.get();
        // ② past its 7-day life
        if (invitation.getExpiresAt() != null && invitation.getExpiresAt().isBefore(LocalDateTime.now())) {
            return JoinEntry.rejected(JoinEntry.Reason.LINK_EXPIRED);
        }
        Optional<UserAccount> account = accountService.getById(invitation.getUserId());
        if (account.isEmpty()) {
            return JoinEntry.rejected(JoinEntry.Reason.LINK_INVALID);
        }
        UserAccount membership = account.get();
        // ③ already completed — they joined, possibly on another device
        if (membership.getActivationTime() != null) {
            return JoinEntry.rejected(JoinEntry.Reason.ALREADY_JOINED);
        }
        // ④ closed or frozen while the invitation was outstanding (E11: off-boarded mid-invite)
        if (membership.getStatus() == AccountStatus.DEACTIVATED
                || membership.getStatus() == AccountStatus.FROZEN) {
            return JoinEntry.rejected(JoinEntry.Reason.MEMBERSHIP_CLOSED);
        }
        // ⑤ usable
        return JoinEntry.usable(
                tenantInfoService == null ? null : tenantInfoService.getTenantName(membership.getTenantId()),
                membership.getNickname(),
                ContactMasking.email(invitation.getEmail()),
                ContactMasking.mobile(invitation.getMobile()));
    }

    /**
     * Returns the address stored ON the invitation, never one supplied by the caller. That is the
     * point: the caller only ever saw a masked value, and accepting an address from them would let
     * a link-holder redirect the code to themselves.
     *
     * <p>The send itself lives in {@code LoginService} — it already owns code issuance and rate
     * limiting, and it already depends on this service, so sending from here would close a cycle.
     */
    @SkipPermissionCheck
    @CrossTenant
    @Override
    public String resolveJoinChannel(String rawToken, String channel) {
        JoinContacts contacts = this.resolveJoinContacts(rawToken);
        String address = "mobile".equalsIgnoreCase(channel) ? contacts.mobile()
                : "email".equalsIgnoreCase(channel) ? contacts.email()
                        : null;
        if (address == null) {
            // Covers both "unknown channel" and "this invitation has no such contact" with one
            // message: telling a link-holder WHICH channels an invitation carries is itself a leak.
            throw new BusinessException("That verification channel is not available for this invitation.");
        }
        return address;
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
