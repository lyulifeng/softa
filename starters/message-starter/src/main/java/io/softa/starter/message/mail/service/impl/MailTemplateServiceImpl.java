package io.softa.starter.message.mail.service.impl;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailScope;
import io.softa.framework.base.placeholder.PlaceholderUtils;
import io.softa.framework.base.placeholder.TemplateEngine;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.message.mail.dto.MailTemplateEffectiveDTO;
import io.softa.starter.message.mail.dto.MailTemplateVariableDTO;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.enums.MailTemplateScope;
import io.softa.starter.message.mail.service.MailSendServerConfigService;
import io.softa.starter.message.mail.service.MailTemplateService;
import io.softa.starter.message.mail.support.TemplateVariableExtractor;
import io.softa.starter.message.shared.TenantScopes;

/**
 * Implementation of {@link MailTemplateService}.
 * <p>
 * Overlay reads fetch the caller's row and the platform row for a code in ONE
 * {@code @CrossTenant} query ({@code tenantId IN (0, currentTenant)}, at most
 * one row per scope thanks to {@code uk(tenantId, code)}) and apply the tier
 * policy in {@link #pickForSend}. Cross-tenant methods are called through the
 * Spring proxy via the {@code self} reference so the AOP advice applies. With
 * multi-tenancy disabled there is a single namespace and the plain
 * tenant-agnostic queries are used instead.
 * <p>
 * Rendering deliberately uses two engines. The subject is plain variable
 * substitution ({@code PlaceholderUtils}): a one-line field with no use for
 * control flow, where a missing variable staying visible as {@code {{ name }}}
 * beats being silently blanked. Bodies go through Pebble — the HTML body via
 * {@code renderHtml}, which HTML-escapes every {@code {{ }}} output (a template
 * embeds trusted markup explicitly with {@code | raw}); the plain-text body via
 * {@code render}, since escaping would corrupt text/plain content.
 */
@Service
public class MailTemplateServiceImpl extends EntityServiceImpl<MailTemplate, Long>
        implements MailTemplateService {

    /**
     * Self-reference to allow {@code @CrossTenant} AOP advice to be applied
     * when calling the cross-tenant query methods from within the same bean.
     */
    @Lazy
    @Autowired
    private MailTemplateService self;

    @Autowired
    private MailSendServerConfigService sendConfigService;

    /**
     * A template may pin a config of its own scope (the plain tenant-scoped
     * {@code getById} — the write path's context is the scope the row is
     * saved into), or a platform config explicitly shared with tenants.
     * The visibility-scoped lookup alone must NOT be trusted here: it also
     * sees unshared platform rows, which would let a tenant template pin
     * platform-internal infrastructure.
     */
    @Override
    public void validatePreferredServerScope(Map<String, Object> row) {
        Object raw = row == null ? null : row.get("preferredServerConfigId");
        if (raw == null || String.valueOf(raw).isBlank()) {
            return;
        }
        long configId;
        try {
            configId = Long.parseLong(String.valueOf(raw));
        } catch (NumberFormatException e) {
            throw new BusinessException("Invalid preferred send server config id: {0}", raw);
        }
        if (findPinnable(configId).isEmpty()) {
            throw new BusinessException(
                    "Send server config {0} is not available in the scope of this template. "
                    + "A template may only pin a send server owned by its own scope, or a "
                    + "platform server shared with tenants.", configId);
        }
    }

    /**
     * The config the current scope may pin: an own-scope row, or a platform
     * row with {@code sharedWithTenants = true}. Single source of the pin
     * rule — used by both the write validation and the Customize copy.
     */
    private Optional<MailSendServerConfig> findPinnable(long configId) {
        Optional<MailSendServerConfig> own = sendConfigService.getById(configId);
        if (own.isPresent()) {
            return own;
        }
        return sendConfigService.findVisibleById(configId)
                .filter(config -> Boolean.TRUE.equals(config.getSharedWithTenants()));
    }

    @Override
    public void validateCodeOverride(Map<String, Object> row) {
        Object raw = row == null ? null : row.get("code");
        if (raw == null || String.valueOf(raw).isBlank() || !TenantScopes.multiTenancyEnabled()
                || TenantScopes.currentTenantOrPlatform() == TenantScopes.PLATFORM) {
            return;
        }
        String code = String.valueOf(raw);
        self.findPlatformByCodeAny(code)
                .filter(platform -> Boolean.FALSE.equals(platform.getOverridable()))
                .ifPresent(platform -> {
                    throw new BusinessException(
                            "Code ''{0}'' belongs to a locked platform template (overridable = false) "
                            + "— it cannot be overridden by a tenant template.", code);
                });
    }

    @Override
    public void assertWritableInCurrentScope(Long id) {
        if (id == null || getById(id).isPresent()) {
            return;
        }
        if (self.findPlatformById(id).isPresent()) {
            throw new BusinessException(
                    "Mail template {0} is platform-owned and cannot be edited or deleted from a "
                    + "tenant scope. Use Customize to create a tenant copy, or delete the tenant "
                    + "copy to revert to the platform template.", id);
        }
    }

    /**
     * Turn the ORM's silent cross-scope no-op (the tenant-filtered pre-read
     * drops platform ids) into an explicit error; a same-value no-op update
     * on an own row still returns false without noise.
     */
    @Override
    public boolean updateOne(MailTemplate entity) {
        boolean updated = super.updateOne(entity);
        if (!updated && entity != null) {
            assertWritableInCurrentScope(entity.getId());
        }
        return updated;
    }

    @Override
    public boolean deleteById(Long id) {
        boolean deleted = super.deleteById(id);
        if (!deleted) {
            assertWritableInCurrentScope(id);
        }
        return deleted;
    }

    @Override
    public MailTemplate resolve(String code) {
        return resolve(code, MailScope.OVERLAY);
    }

    @Override
    public MailTemplate resolve(String code, MailScope scope) {
        return selectByPolicy(code, scope, true);
    }

    @Override
    public MailTemplate resolveAny(String code) {
        return selectByPolicy(code, MailScope.OVERLAY, false);
    }

    private MailTemplate selectByPolicy(String code, MailScope scope, boolean enabledOnly) {
        if (!TenantScopes.multiTenancyEnabled()) {
            // Single tenant = single namespace: no overlay, no tier policy.
            Filters filters = new Filters().eq(MailTemplate::getCode, code);
            if (enabledOnly) {
                filters.eq(MailTemplate::getIsEnabled, true);
            }
            return searchOne(filters).orElseThrow(() -> notFound(code));
        }
        ScopePair pair = ScopePair.of(self.findVisibleByCode(code), TenantScopes.currentTenantOrPlatform());
        return pickForSend(pair.platform(), pair.own(), scope, enabledOnly)
                .orElseThrow(() -> notFound(code));
    }

    private static BusinessException notFound(String code) {
        return new BusinessException("No mail template found for code ''{0}''.", code);
    }

    /**
     * The tier policy, isolated for direct testing. {@code platform} / {@code
     * own} are the raw rows of one code (any enabled state, either nullable):
     * <ol>
     *   <li>{@code PLATFORM_ONLY} → the platform row.</li>
     *   <li>A platform row locked with {@code overridable = false} → the
     *       platform row, even when disabled (the lock must hold; a disabled
     *       locked platform template resolves to nothing, never to the
     *       shadowed tenant row).</li>
     *   <li>Otherwise the own row, falling back to the platform row.</li>
     * </ol>
     * With {@code enabledOnly}, disabled rows are dropped from the outcome —
     * but the lock is evaluated on the raw platform row first.
     */
    static Optional<MailTemplate> pickForSend(MailTemplate platform, MailTemplate own,
                                              MailScope scope, boolean enabledOnly) {
        boolean locked = platform != null && Boolean.FALSE.equals(platform.getOverridable());
        MailTemplate platformUsable = enabledOnly ? filterEnabled(platform) : platform;
        MailTemplate ownUsable = enabledOnly ? filterEnabled(own) : own;
        if (scope == MailScope.PLATFORM_ONLY || locked) {
            return Optional.ofNullable(platformUsable);
        }
        return Optional.ofNullable(ownUsable != null ? ownUsable : platformUsable);
    }

    private static MailTemplate filterEnabled(MailTemplate template) {
        return template != null && Boolean.TRUE.equals(template.getIsEnabled()) ? template : null;
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformByCode(String code) {
        return searchOne(new Filters()
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM)
                .eq(MailTemplate::getCode, code)
                .eq(MailTemplate::getIsEnabled, true));
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformByCodeAny(String code) {
        return searchOne(new Filters()
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM)
                .eq(MailTemplate::getCode, code));
    }

    @Override
    @CrossTenant
    public List<MailTemplate> findVisibleByCode(String code) {
        return searchList(new Filters()
                .eq(MailTemplate::getCode, code)
                .in(MailTemplate::getTenantId, TenantScopes.currentPlusPlatform()));
    }

    @Override
    @CrossTenant
    public List<MailTemplate> findVisibleList() {
        return searchList(new Filters()
                .in(MailTemplate::getTenantId, TenantScopes.currentPlusPlatform()));
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformById(Long id) {
        return searchOne(new Filters()
                .eq(MailTemplate::getId, id)
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM));
    }

    @Override
    public List<MailTemplateEffectiveDTO> listEffective() {
        if (!TenantScopes.multiTenancyEnabled()) {
            // Single namespace: every row is the caller's own; nothing governs it.
            return searchList().stream()
                    .sorted(Comparator.comparing(MailTemplate::getCode))
                    .map(row -> MailTemplateEffectiveDTO.from(row, MailTemplateScope.OWN, true))
                    .toList();
        }
        long caller = TenantScopes.currentTenantOrPlatform();
        List<MailTemplateEffectiveDTO> result = new ArrayList<>();
        for (ScopePair pair : groupVisibleByCode(caller).values()) {
            boolean governing = pair.platform() == null
                    || !Boolean.FALSE.equals(pair.platform().getOverridable());
            if (pair.own() != null) {
                MailTemplateScope scope = pair.platform() != null
                        ? MailTemplateScope.CUSTOMIZED : MailTemplateScope.OWN;
                result.add(MailTemplateEffectiveDTO.from(pair.own(), scope, governing));
            } else if (pair.platform() != null) {
                // The platform scope's own rows also sit in the platform slot —
                // to their owner they are OWN, not inherited.
                MailTemplateScope scope = caller == TenantScopes.PLATFORM
                        ? MailTemplateScope.OWN : MailTemplateScope.INHERITED;
                result.add(MailTemplateEffectiveDTO.from(pair.platform(), scope, governing));
            }
        }
        result.sort(Comparator.comparing(MailTemplateEffectiveDTO::getCode));
        return result;
    }

    @Override
    public List<MailTemplate> resolveEffectiveList(boolean enabledOnly) {
        if (!TenantScopes.multiTenancyEnabled()) {
            Filters filters = new Filters();
            if (enabledOnly) {
                filters.eq(MailTemplate::getIsEnabled, true);
            }
            return searchList(filters).stream()
                    .sorted(Comparator.comparing(MailTemplate::getCode))
                    .toList();
        }
        long caller = TenantScopes.currentTenantOrPlatform();
        return groupVisibleByCode(caller).values().stream()
                .map(pair -> pickForSend(pair.platform(), pair.own(), MailScope.OVERLAY, enabledOnly))
                .flatMap(Optional::stream)
                .sorted(Comparator.comparing(MailTemplate::getCode))
                .toList();
    }

    private Map<String, ScopePair> groupVisibleByCode(long caller) {
        Map<String, ScopePair> byCode = new LinkedHashMap<>();
        for (MailTemplate row : self.findVisibleList()) {
            byCode.merge(row.getCode(), ScopePair.of(List.of(row), caller),
                    (a, b) -> new ScopePair(
                            a.platform() != null ? a.platform() : b.platform(),
                            a.own() != null ? a.own() : b.own()));
        }
        return byCode;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long customize(Long platformTemplateId) {
        if (!TenantScopes.multiTenancyEnabled()) {
            throw new BusinessException(
                    "Multi-tenancy is disabled — there is no platform/tenant overlay to customize; "
                    + "edit the template directly.");
        }
        if (TenantScopes.currentTenantOrPlatform() == TenantScopes.PLATFORM) {
            throw new BusinessException(
                    "Customize is a tenant action — the platform scope owns the template already; "
                    + "edit it directly.");
        }
        MailTemplate platform = self.findPlatformById(platformTemplateId)
                .orElseThrow(() -> new BusinessException(
                        "No platform mail template found for id {0}.", platformTemplateId));
        if (Boolean.FALSE.equals(platform.getOverridable())) {
            throw new BusinessException(
                    "Platform template ''{0}'' is locked (overridable = false) and cannot be customized.",
                    platform.getCode());
        }
        if (searchOne(new Filters().eq(MailTemplate::getCode, platform.getCode())).isPresent()) {
            throw new BusinessException(
                    "Template ''{0}'' is already customized in this tenant — edit or delete the "
                    + "existing copy instead.", platform.getCode());
        }
        MailTemplate copy = copyForTenant(platform);
        // Carry the pin only if this tenant's scope may pin it; the write
        // endpoints enforce the same rule, so an uncarryable pin is cleared
        // rather than rejected — the copy falls back to default dispatch.
        if (copy.getPreferredServerConfigId() != null
                && findPinnable(copy.getPreferredServerConfigId()).isEmpty()) {
            copy.setPreferredServerConfigId(null);
        }
        // createOne runs in the plain tenant scope (NOT @CrossTenant), so the
        // ORM stamps the caller's tenant id onto the copy.
        return createOne(copy);
    }

    /**
     * The content copy of the Customize action, isolated for direct testing:
     * carries the authored content and defaults, keeps the {@code code} (the
     * shadowing key), and leaves identity (id / tenantId / audit) to the
     * create path. {@code overridable} is a platform-row policy and stays
     * null on the tenant copy.
     */
    static MailTemplate copyForTenant(MailTemplate platform) {
        MailTemplate copy = new MailTemplate();
        copy.setCode(platform.getCode());
        copy.setName(platform.getName());
        copy.setDescription(platform.getDescription());
        copy.setSubject(platform.getSubject());
        copy.setBodyHtml(platform.getBodyHtml());
        copy.setBodyText(platform.getBodyText());
        copy.setBodyMode(platform.getBodyMode());
        copy.setIsEnabled(platform.getIsEnabled());
        copy.setDefaultPriority(platform.getDefaultPriority());
        copy.setReplyTo(platform.getReplyTo());
        copy.setAttachments(platform.getAttachments());
        copy.setPreferredServerConfigId(platform.getPreferredServerConfigId());
        return copy;
    }

    @Override
    public String renderSubject(MailTemplate template, Map<String, Object> variables) {
        return PlaceholderUtils.replacePlaceholders(template.getSubject(), variables);
    }

    @Override
    public String renderBodyHtml(MailTemplate template, Map<String, Object> variables) {
        return template.getBodyHtml() == null
                ? null : TemplateEngine.renderHtml(template.getBodyHtml(), variables);
    }

    @Override
    public String renderBodyText(MailTemplate template, Map<String, Object> variables) {
        return template.getBodyText() == null
                ? null : TemplateEngine.render(template.getBodyText(), variables);
    }

    @Override
    public List<MailTemplateVariableDTO> listVariables(String code) {
        // resolveAny: variable inputs must work for a disabled template —
        // authoring tools verify BEFORE enabling.
        return TemplateVariableExtractor.extract(resolveAny(code));
    }

    @Override
    public MailTemplate getRequiredById(Long id) {
        return getById(id).orElseThrow(() -> new BusinessException(
                "No mail template found for id {0}.", id));
    }

    @Override
    public List<MailTemplateVariableDTO> listVariablesById(Long id) {
        return TemplateVariableExtractor.extract(getRequiredById(id));
    }

    /**
     * The (platform row, own row) pair of one code as seen by {@code caller}.
     * {@code tenant_id = 0} rows always land in {@code platform} — including
     * for a platform-scope caller, whose {@code own} stays empty (so
     * {@code PLATFORM_ONLY} and the overlay fallback both find the row there).
     */
    record ScopePair(MailTemplate platform, MailTemplate own) {

        static ScopePair of(List<MailTemplate> rows, long caller) {
            MailTemplate platform = null;
            MailTemplate own = null;
            for (MailTemplate row : rows) {
                long rowTenant = row.getTenantId() == null ? TenantScopes.PLATFORM : row.getTenantId();
                if (rowTenant == TenantScopes.PLATFORM) {
                    platform = row;
                } else if (rowTenant == caller) {
                    own = row;
                }
            }
            return new ScopePair(platform, own);
        }
    }
}
