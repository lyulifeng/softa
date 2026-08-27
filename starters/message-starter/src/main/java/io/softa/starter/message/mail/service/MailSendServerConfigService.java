package io.softa.starter.message.mail.service;

import java.util.List;
import java.util.Optional;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.message.mail.dto.ConnectivityTestResultDTO;
import io.softa.starter.message.mail.entity.MailSendServerConfig;

/**
 * CRUD service for outgoing mail server configurations.
 */
public interface MailSendServerConfigService extends EntityService<MailSendServerConfig, Long> {

    /**
     * Find the current tenant's default enabled sending config.
     * ORM automatically applies {@code WHERE tenant_id = currentTenantId}.
     */
    Optional<MailSendServerConfig> findTenantDefault();

    /**
     * Find the platform-level default sending config (tenant_id = 0).
     * Uses {@code @CrossTenant} to bypass ORM tenant filtering.
     */
    Optional<MailSendServerConfig> findPlatformDefault();

    /**
     * Load a config by id within the caller's visibility scope: the caller's
     * own tenant plus the platform tier (tenant_id = 0). Send records
     * legitimately reference platform-level configs, which the implicit
     * single-tenant filter would hide — dispatch and retry paths must resolve
     * ids through this method rather than {@code getById}.
     */
    Optional<MailSendServerConfig> findVisibleById(Long id);

    /**
     * The sender configs the caller may select: the caller's own enabled
     * configs plus the enabled platform configs shared with tenants
     * ({@code sharedWithTenants = true}), ordered by {@code sequence}.
     * Backs sender pickers — including the template editor's
     * {@code preferredServerConfigId} dropdown, whose choices must match what
     * {@code validatePreferredServerScope} accepts.
     */
    List<MailSendServerConfig> listSelectable();

    /**
     * Reject — with an explanatory error — a write addressed at a platform
     * config from a tenant scope. The ORM already makes such writes no-ops
     * (the tenant-filtered pre-read drops invisible ids); this turns the
     * silent no-op into an actionable message. No-op when {@code id} is null,
     * when the row is visible in the caller's own scope, or when the id
     * matches nothing at all.
     *
     * @param id the row id targeted by an update/delete
     * @throws io.softa.framework.base.exception.BusinessException if the id
     *         addresses a platform config and the caller is a tenant scope
     */
    void assertWritableInCurrentScope(Long id);

    /**
     * Demote every other default config in the current tenant scope so that
     * {@code keptId} is the only one left with {@code isDefault = true}. Called
     * by the write endpoints after a row is saved as default; rows authored by
     * init scripts are untouched (the dispatcher's {@code sequence} ordering
     * remains the tie-break for those).
     */
    void demoteOtherDefaults(Long keptId);

    /**
     * Test SMTP connectivity and authentication for the config identified by {@code id}.
     */
    ConnectivityTestResultDTO testConnectivity(Long id);

    /**
     * Test connectivity using a transient config object (e.g. before saving).
     */
    ConnectivityTestResultDTO testConnectivity(MailSendServerConfig config);
}
