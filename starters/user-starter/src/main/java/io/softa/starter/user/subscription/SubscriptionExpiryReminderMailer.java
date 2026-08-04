package io.softa.starter.user.subscription;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.softa.framework.base.message.MailRequestMessage;
import io.softa.framework.base.message.SubscriptionExpiryReminderMessage;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.constant.RoleConstant;
import io.softa.starter.user.entity.Role;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserRoleRel;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserRoleRelService;

/**
 * Emails a subscription-expiry reminder to every {@code TENANT_ADMIN} of a tenant. Driven by
 * {@link SubscriptionExpiryReminderConsumer} off the tenant-starter reminder MQ message (which keeps
 * tenant-starter ⊥ user-starter). Reuses the existing templated-mail pipeline: it publishes one
 * {@link MailRequestMessage} per admin (the record has no bcc, so a shared recipient list would leak
 * addresses — same reason {@code UserInvitationService} sends per-recipient); the generic
 * {@code MailRequestPublisher} relays each to Pulsar and message-starter renders the template.
 *
 * <p>Must run inside the tenant's context ({@code inTenantContext}) so the multi-tenant {@code Role} /
 * {@code UserRoleRel} lookups resolve to this tenant's admins only. The mail template
 * ({@value #TEMPLATE_EXPIRY_REMINDER}) is a platform ({@code tenantId=0}) row — the mail consumer runs with
 * no tenant context — so every tenant-specific value is passed as a template variable.
 */
@Slf4j
@Service
public class SubscriptionExpiryReminderMailer {

    /** Platform ({@code tenantId=0}) mail template codes, seeded in {@code MailTemplate.System.json}.
     *  A purchased plan gets renewal wording; a trial gets upgrade wording. */
    static final String TEMPLATE_EXPIRY_REMINDER = "subscription.expiry-reminder";
    static final String TEMPLATE_TRIAL_EXPIRY_REMINDER = "subscription.trial-expiry-reminder";
    /**
     * Used when a later period exists but does not start the day after this one ends. "Please renew" is the
     * wrong ask — they have renewed — so this template names the uncovered stretch instead.
     */
    static final String TEMPLATE_GAP_REMINDER = "subscription.gap-reminder";
    /**
     * Used when a LOWER-tier period covers the day after this one ends — the tenant is not cut off, it drops to
     * that plan. Every other template promises a loss of access, which here would simply be false: each tenant
     * owns an open-ended floor-plan period underneath whatever it bought, so a purchased period lapsing means a
     * downgrade, not a lockout. Says which plan takes over and what buying again would keep.
     */
    static final String TEMPLATE_DOWNGRADE_REMINDER = "subscription.downgrade-reminder";

    private final RoleService roleService;
    private final UserRoleRelService userRoleRelService;
    private final UserAccountService accountService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionExpiryReminderMailer(RoleService roleService,
                                            UserRoleRelService userRoleRelService,
                                            UserAccountService accountService,
                                            ApplicationEventPublisher eventPublisher) {
        this.roleService = roleService;
        this.userRoleRelService = userRoleRelService;
        this.accountService = accountService;
        this.eventPublisher = eventPublisher;
    }

    /** Publish one reminder mail per {@code TENANT_ADMIN}. Assumes it already runs in the tenant's context. */
    public void remindAdmins(SubscriptionExpiryReminderMessage message) {
        Role adminRole = roleService.searchOne(
                new Filters().eq(Role::getCode, RoleConstant.CODE_TENANT_ADMIN)).orElse(null);
        if (adminRole == null) {
            log.warn("TENANT_ADMIN role not found for tenant {}; skipping expiry reminder", message.tenantId());
            return;
        }
        List<Long> adminUserIds = userRoleRelService.getDistinctFieldValue(
                UserRoleRel::getUserId, new Filters().eq(UserRoleRel::getRoleId, adminRole.getId()));
        if (adminUserIds.isEmpty()) {
            log.warn("No TENANT_ADMIN users for tenant {}; skipping expiry reminder", message.tenantId());
            return;
        }
        List<String> emails = accountService.getByIds(adminUserIds).stream()
                .map(UserAccount::getEmail)
                .filter(email -> email != null && !email.isBlank())
                .distinct()
                .toList();
        if (emails.isEmpty()) {
            log.warn("TENANT_ADMIN users for tenant {} have no email; skipping expiry reminder", message.tenantId());
            return;
        }

        Map<String, Object> variables = Map.of(
                "tenantName", message.tenantName() == null ? "" : message.tenantName(),
                "planId", message.planId() == null ? "" : message.planId(),
                "expiryDate", message.effectiveTo() == null ? "" : message.effectiveTo(),
                "daysLeft", message.daysLeft(),
                "nextStartDate", message.nextStartDate() == null ? "" : message.nextStartDate(),
                "successorPlanId", message.successorPlanId() == null ? "" : message.successorPlanId());
        // Four mutually exclusive asks, ordered by what the reader actually loses.
        //
        // The downgrade case is FIRST and outranks everything, because it is the only one where access does
        // not stop: a lower-tier period covers the day after, so the tenant keeps working with fewer modules.
        // Sending any of the other three here would tell a paying customer they are about to lose access when
        // they are not — and a customer who discovers the claim was false stops believing the next reminder.
        //
        // The gap case is next and wins over `trial`, because what matters is the uncovered stretch, not which
        // kind of period is ending: a trial that ends with a paid period booked for later still leaves the
        // customer with nothing in between, and "upgrade to keep access" would read as though nothing had
        // been bought.
        String templateCode;
        if (message.successorPlanId() != null && !message.successorPlanId().isBlank()) {
            templateCode = TEMPLATE_DOWNGRADE_REMINDER;
        } else if (message.nextStartDate() != null && !message.nextStartDate().isBlank()) {
            templateCode = TEMPLATE_GAP_REMINDER;
        } else if (message.trial()) {
            templateCode = TEMPLATE_TRIAL_EXPIRY_REMINDER;
        } else {
            templateCode = TEMPLATE_EXPIRY_REMINDER;
        }
        for (String email : emails) {
            eventPublisher.publishEvent(new MailRequestMessage(List.of(email), templateCode, variables));
        }
        // The template code itself, not a re-derivation of it: a two-branch guess here logged a gap reminder
        // as "subscription-expiry", so the log disagreed with the mail that was actually sent — exactly the
        // wrong signal when someone is investigating why a customer got the wording they did.
        log.info("Published {} '{}' reminder mail(s) for tenant {} ({} day(s) left, expires {})",
                emails.size(), templateCode,
                message.tenantId(), message.daysLeft(), message.effectiveTo());
    }
}
