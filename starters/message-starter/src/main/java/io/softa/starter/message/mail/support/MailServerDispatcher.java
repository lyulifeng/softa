package io.softa.starter.message.mail.support;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailScope;
import io.softa.starter.message.mail.entity.MailReceiveServerConfig;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.MailReceiveServerConfigService;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

/**
 * Resolves the effective mail server config for sending or receiving.
 * <p>
 * Resolution order for both directions:
 * <ol>
 *   <li>Tenant's own default config (ORM auto-filters by current tenant_id)</li>
 *   <li>Platform-level default config (tenant_id = 0, via {@code @CrossTenant})</li>
 *   <li>{@link BusinessException} if neither is found</li>
 * </ol>
 * A send declaring {@link MailScope#PLATFORM_ONLY} skips step 1 — platform
 * mail must not route through tenant-controlled SMTP.
 */
@Slf4j
@Component
public class MailServerDispatcher {

    @Autowired
    private MailSendServerConfigService sendConfigService;

    @Autowired
    private MailReceiveServerConfigService receiveConfigService;

    @Autowired
    private MailConfigCache configCache;

    /**
     * Resolve the effective sending (SMTP) server config for the current tenant.
     * Cached for a few minutes — evict via {@link MailConfigCache#evictById(Long)}
     * on config update.
     */
    public MailSendServerConfig resolveSend() {
        return resolveSend(MailScope.OVERLAY);
    }

    /**
     * {@link #resolveSend()} under an explicit tier policy:
     * {@code PLATFORM_ONLY} skips the tenant default and resolves the platform
     * default only (cached under the platform key, so the tenant's own default
     * cache entry is neither read nor poisoned).
     */
    public MailSendServerConfig resolveSend(MailScope scope) {
        MailSendServerConfig config = scope == MailScope.PLATFORM_ONLY
                ? configCache.getPlatformDefault(
                        () -> sendConfigService.findPlatformDefault().orElse(null))
                : configCache.getDefault(
                        () -> sendConfigService.findTenantDefault()
                                .or(sendConfigService::findPlatformDefault)
                                .orElse(null));
        if (config == null) {
            // ERROR on purpose: this is an ops-actionable deployment gap
            // (no enabled default at tenant or platform level), surfaced at
            // send time so alerting catches it before users report it.
            log.error("Mail send rejected: no enabled default send server config "
                    + "available. scope={}, tenantId={}", scope, currentTenantId());
            throw new BusinessException(scope == MailScope.PLATFORM_ONLY
                    ? "No enabled platform-level default sending mail server is configured. "
                            + "Ask the platform operator to add and enable one."
                    : "No sending mail server is configured for this tenant. "
                            + "Ask your administrator to add and enable one.");
        }
        return config;
    }

    /**
     * Resolve a specific sending config by ID, bypassing dispatch logic.
     */
    public MailSendServerConfig resolveSendById(Long id) {
        // Visibility-scoped lookup: records may reference platform-level
        // (tenant 0) configs that the implicit tenant filter would hide.
        MailSendServerConfig config = configCache.getById(id,
                () -> sendConfigService.findVisibleById(id).orElse(null));
        if (config == null) {
            throw new BusinessException(
                    "Mail send server config with ID {0} not found.", id);
        }
        return config;
    }

    /**
     * Resolve the effective receiving (IMAP/POP3) server config for the current tenant.
     */
    public MailReceiveServerConfig resolveReceive() {
        MailReceiveServerConfig config = receiveConfigService.findTenantDefault()
                .or(receiveConfigService::findPlatformDefault)
                .orElse(null);
        if (config == null) {
            log.error("Mail receive unavailable: no enabled default receive server config "
                    + "at tenant or platform level. tenantId={}", currentTenantId());
            throw new BusinessException(
                    "No receiving mail server is configured for this tenant. "
                    + "Ask your administrator to add and enable one.");
        }
        return config;
    }

    /**
     * Resolve a specific receiving config by ID, bypassing dispatch logic.
     */
    public MailReceiveServerConfig resolveReceiveById(Long id) {
        return receiveConfigService.findVisibleById(id)
                .orElseThrow(() -> new BusinessException(
                        "Mail receive server config with ID {0} not found.", id));
    }

    private static Long currentTenantId() {
        Context context = ContextHolder.getContext();
        return context != null ? context.getTenantId() : null;
    }
}
