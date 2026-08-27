package io.softa.starter.message.mail.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.softa.framework.base.message.MessageScope;
import io.softa.framework.orm.service.EntityService;
import io.softa.starter.message.mail.dto.MailTemplateVariableDTO;
import io.softa.starter.message.mail.entity.MailTemplate;

/**
 * CRUD and resolution service for {@link MailTemplate}.
 * <p>
 * The two template tiers are fully separate namespaces — no overlay, no
 * fallback:
 * <pre>
 *   scope = TENANT   → the current scope's own template (tenants receive
 *                      their rows at provisioning via the application's
 *                      per-tenant seed files and edit them freely)
 *   scope = PLATFORM → the platform tier (tenant_id = -1) only
 * </pre>
 */
public interface MailTemplateService extends EntityService<MailTemplate, Long> {

    /**
     * Validate the {@code preferredServerConfigId} carried by a template write
     * payload: a template may only pin a send server config owned by its own
     * tenant scope. Platform server configs are invisible to tenants — they
     * are reached solely by the dispatcher's default fallback. No-op when the
     * payload does not carry the field.
     *
     * @param row the raw write payload (create or update)
     */
    void validatePreferredServerScope(Map<String, Object> row);

    /**
     * Reject — with an explanatory error — a write addressed at a platform
     * row from a tenant scope. The ORM already makes such writes no-ops (the
     * tenant-filtered pre-read drops invisible ids); this turns the silent
     * no-op into an actionable message. No-op when {@code id} is null, when
     * the row is visible in the caller's own scope, or when the id matches
     * nothing at all.
     *
     * @param id the row id targeted by an update/delete
     * @throws io.softa.framework.base.exception.BusinessException if the id
     *         addresses a platform row and the caller is a tenant scope
     */
    void assertWritableInCurrentScope(Long id);

    /**
     * Resolve the matching <b>active</b> template for {@code code} under
     * {@link MessageScope#TENANT}.
     *
     * @param code template code, e.g. {@code "USER_WELCOME"}
     * @return the resolved template
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    MailTemplate resolve(String code);

    /**
     * Resolve the matching <b>active</b> template for {@code code} in the
     * tier the policy names — {@code TENANT} reads the current scope's own
     * rows (which for a platform session ARE the platform rows),
     * {@code PLATFORM} explicitly reads the platform tier regardless of the
     * ambient context (e.g. platform mail rendered inside a tenant context).
     *
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    MailTemplate resolve(String code, MessageScope scope);

    /**
     * Like {@link #resolve(String)} but WITHOUT the active-control filter —
     * for read-only authoring tools (preview, variable listing) where a
     * disabled template must stay inspectable so it can be verified before
     * being activated. Delivery paths must keep using {@link #resolve(String)}.
     */
    MailTemplate resolveAny(String code);

    /**
     * Query the platform-tier (tenant_id = -1) template, bypassing tenant
     * isolation. Annotated with {@code @CrossTenant} in the implementation so
     * that ORM tenant filters are suppressed for the duration of the call.
     *
     * @param code template code
     * @return the active platform template if found
     */
    Optional<MailTemplate> findPlatformByCode(String code);

    /**
     * {@link #findPlatformByCode(String)} without the active-control
     * filter.
     */
    Optional<MailTemplate> findPlatformByCodeAny(String code);

    /**
     * The platform row with this id, if any — {@code @CrossTenant} in the
     * implementation. Backs the platform-row write probe.
     */
    Optional<MailTemplate> findPlatformById(Long id);

    /**
     * Render the subject of {@code template} by substituting {@code variables}
     * into {@code {{ key }}} placeholders.
     */
    String renderSubject(MailTemplate template, Map<String, Object> variables);

    /**
     * Render the HTML body of {@code template} by substituting {@code variables}
     * into {@code {{ key }}} placeholders. Returns {@code null} when the template
     * has no HTML body (PLAIN-mode templates).
     */
    String renderBodyHtml(MailTemplate template, Map<String, Object> variables);

    /**
     * Render the plain-text body of {@code template} by substituting {@code variables}
     * into {@code {{ key }}} placeholders. Returns {@code null} when the template
     * has no plain-text body (HTML-only / HTML_WITH_DERIVED_PLAIN templates).
     */
    String renderBodyText(MailTemplate template, Map<String, Object> variables);

    /**
     * List the distinct placeholder tokens of the template resolved for
     * {@code code}, in first-appearance order across subject / bodyHtml /
     * bodyText — for variable-input UIs (Send Test / Preview dialogs).
     */
    List<MailTemplateVariableDTO> listVariables(String code);

    /**
     * Load one template row by id for editor tooling (preview / variable
     * listing): no code resolution, no active-control filter — the row
     * being edited is the row inspected. Tenant visibility follows the ORM
     * filter, i.e. exactly the rows whose edit form the caller can open.
     *
     * @throws io.softa.framework.base.exception.BusinessException if the row does not exist
     */
    MailTemplate getRequiredById(Long id);

    /** {@link #listVariables(String)} addressed by row id (editor tooling). */
    List<MailTemplateVariableDTO> listVariablesById(Long id);
}
