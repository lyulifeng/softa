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
                "daysLeft", message.daysLeft());
        // Trial → upgrade wording; purchased plan → renewal wording (two platform templates).
        String templateCode = message.trial() ? TEMPLATE_TRIAL_EXPIRY_REMINDER : TEMPLATE_EXPIRY_REMINDER;
        for (String email : emails) {
            eventPublisher.publishEvent(new MailRequestMessage(List.of(email), templateCode, variables));
        }
        log.info("Published {} {} reminder mail(s) for tenant {} ({} day(s) left, expires {})",
                emails.size(), message.trial() ? "trial-expiry" : "subscription-expiry",
                message.tenantId(), message.daysLeft(), message.effectiveTo());
    }
}
