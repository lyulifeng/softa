package io.softa.starter.message.mail.service.impl;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MessageScope;
import io.softa.framework.base.placeholder.PlaceholderUtils;
import io.softa.framework.base.placeholder.TemplateEngine;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.domain.FilterControl;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.message.mail.dto.MailTemplateVariableDTO;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.service.MailSendServerConfigService;
import io.softa.starter.message.mail.service.MailTemplateService;
import io.softa.starter.message.mail.support.TemplateVariableExtractor;
import io.softa.starter.message.shared.TenantScopes;

/**
 * Implementation of {@link MailTemplateService}.
 * <p>
 * Tier-pure resolution: {@code TENANT} reads through the plain ORM tenant
 * filter (the current scope's own rows — which for a platform session are the
 * platform rows), {@code PLATFORM} explicitly targets {@code tenant_id = -1}
 * via {@code @CrossTenant} regardless of the ambient context. Cross-tenant
 * methods are called through the Spring proxy via the {@code self} reference
 * so the AOP advice applies.
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
     * The lookup is deliberately the plain tenant-scoped {@code getById} — the
     * write path's context is the scope the row is saved into, so "visible in
     * this read" and "owned by the template's scope" are the same set. Platform
     * configs are invisible to tenants by design and can never be pinned.
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
        if (sendConfigService.getById(configId).isEmpty()) {
            throw new BusinessException(
                    "Send server config {0} is not available in the scope of this template. "
                    + "A template may only pin a send server owned by its own scope.", configId);
        }
    }

    @Override
    public void assertWritableInCurrentScope(Long id) {
        if (id == null || getById(id).isPresent()) {
            return;
        }
        if (self.findPlatformById(id).isPresent()) {
            throw new BusinessException(
                    "Mail template {0} is platform-owned and cannot be edited or deleted from a "
                    + "tenant scope.", id);
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
        return resolve(code, MessageScope.TENANT);
    }

    @Override
    public MailTemplate resolve(String code, MessageScope scope) {
        Optional<MailTemplate> result;
        if (scope == MessageScope.PLATFORM && TenantScopes.multiTenancyEnabled()) {
            result = self.findPlatformByCode(code);
        } else {
            // TENANT (and any scope with multi-tenancy off): the current
            // scope's own rows via the plain ORM tenant filter. The
            // active-control filter (active = true) is applied automatically.
            result = searchOne(new Filters().eq(MailTemplate::getCode, code));
        }
        return result.orElseThrow(() -> new BusinessException(
                "No mail template found for code ''{0}''.", code));
    }

    @Override
    public MailTemplate resolveAny(String code) {
        return searchOne(inactiveIncluded(new Filters().eq(MailTemplate::getCode, code)))
                .orElseThrow(() -> new BusinessException(
                        "No mail template found for code ''{0}''.", code));
    }

    /**
     * A read that deliberately reaches disabled rows too: the model is
     * {@code activeControl}, so {@code active = true} is appended to every
     * plain FlexQuery — authoring tools (preview / variables) and the
     * platform-row write probe must see a disabled row, and an id-addressed
     * {@code getById} already bypasses the filter by construction.
     */
    private static FlexQuery inactiveIncluded(Filters filters) {
        FlexQuery flexQuery = new FlexQuery(filters);
        flexQuery.setFilterControl(FilterControl.bypassActiveControl());
        return flexQuery;
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformByCode(String code) {
        // active = true is appended by the framework's active control.
        return searchOne(new Filters()
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM)
                .eq(MailTemplate::getCode, code));
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformByCodeAny(String code) {
        return searchOne(inactiveIncluded(new Filters()
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM)
                .eq(MailTemplate::getCode, code)));
    }

    @Override
    @CrossTenant
    public Optional<MailTemplate> findPlatformById(Long id) {
        // The write probe must recognise a DISABLED platform row too, else the
        // explanatory rejection degrades back to a silent no-op.
        return searchOne(inactiveIncluded(new Filters()
                .eq(MailTemplate::getId, id)
                .eq(MailTemplate::getTenantId, TenantScopes.PLATFORM)));
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
}
