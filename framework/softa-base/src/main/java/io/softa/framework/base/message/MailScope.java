package io.softa.framework.base.message;

/**
 * Tier-selection policy for one templated-mail send in a multi-tenant
 * deployment. Decides which tier the template AND the sending server are
 * resolved from — the two axes always follow the same declared scope, while
 * an explicit {@code serverConfigId} / {@code templateId} on the request
 * still wins over either.
 * <p>
 * Carried on {@code SendMailDTO.scope} and {@code MailRequestMessage.scope};
 * {@code null} means {@link #OVERLAY}. With multi-tenancy disabled there is
 * a single tier and the value has no effect.
 */
public enum MailScope {

    /**
     * Default. Tenant-overlay resolution: the current tenant's template /
     * default server wins, falling back to the platform tier ({@code
     * tenant_id = 0}). A platform template marked {@code overridable = false}
     * still resists the tenant override on the content axis.
     */
    OVERLAY,

    /**
     * Platform tier only: the tenant's customized template and the tenant's
     * default server are both skipped. For platform-owned mail — billing,
     * security, compliance — that must neither be re-worded by a tenant
     * template nor routed through tenant-controlled SMTP.
     */
    PLATFORM_ONLY
}
