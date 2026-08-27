package io.softa.starter.message.mail.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.softa.framework.base.message.MailScope;
import io.softa.framework.orm.service.EntityService;
import io.softa.starter.message.mail.dto.MailTemplateEffectiveDTO;
import io.softa.starter.message.mail.dto.MailTemplateVariableDTO;
import io.softa.starter.message.mail.entity.MailTemplate;

/**
 * CRUD and resolution service for {@link MailTemplate}.
 * <p>
 * Template resolution prefers a tenant template, falling back to the
 * platform-defined default:
 * <pre>
 *   tenant template → platform template (tenant_id = 0) → BusinessException
 * </pre>
 * Two policy switches modulate the overlay: a platform template with
 * {@code overridable = false} always wins over a tenant template of the same
 * code (content lock), and a send declaring {@link MailScope#PLATFORM_ONLY}
 * skips the tenant tier entirely.
 */
public interface MailTemplateService extends EntityService<MailTemplate, Long> {

    /**
     * Validate the {@code preferredServerConfigId} carried by a template write
     * payload: a template may pin a send server config owned by its own tenant
     * scope, or a platform config explicitly shared with tenants
     * ({@code sharedWithTenants = true}). Anything else — another tenant's
     * config, or platform-internal infrastructure — is rejected. No-op when
     * the payload does not carry the field.
     *
     * @param row the raw write payload (create or update)
     */
    void validatePreferredServerScope(Map<String, Object> row);

    /**
     * Validate the {@code code} carried by a tenant <b>create</b> payload:
     * creating a tenant template whose code shadows a platform template is
     * the override mechanism and stays allowed, but a code locked by the
     * platform ({@code overridable = false}) is rejected outright — such a
     * row could never take effect. No-op for platform-scope callers and
     * single-tenant deployments.
     *
     * @param row the raw create payload
     */
    void validateCodeOverride(Map<String, Object> row);

    /**
     * Reject — with an explanatory error — a write addressed at a platform
     * row from a tenant scope. The ORM already makes such writes no-ops (the
     * tenant-filtered pre-read drops invisible ids); this turns the silent
     * no-op into an actionable message. No-op when {@code id} is null, when
     * the row is visible in the caller's own scope (the normal write path
     * decides), or when the id matches nothing at all.
     *
     * @param id the row id targeted by an update/delete
     * @throws io.softa.framework.base.exception.BusinessException if the id
     *         addresses a platform row and the caller is a tenant scope
     */
    void assertWritableInCurrentScope(Long id);

    /**
     * Resolve the matching template for {@code code} in the current tenant
     * under the {@link MailScope#OVERLAY} policy.
     *
     * @param code template code, e.g. {@code "USER_WELCOME"}
     * @return the resolved template
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    MailTemplate resolve(String code);

    /**
     * Resolve the matching <b>enabled</b> template for {@code code} under the
     * given tier policy:
     * <ul>
     *   <li>{@link MailScope#OVERLAY} (or null) — tenant template first,
     *       platform fallback; a platform template with
     *       {@code overridable = false} beats the tenant override.</li>
     *   <li>{@link MailScope#PLATFORM_ONLY} — platform tier only.</li>
     * </ul>
     *
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    MailTemplate resolve(String code, MailScope scope);

    /**
     * Query the platform-level (tenant_id = 0) template, bypassing tenant
     * isolation. Annotated with {@code @CrossTenant} in the implementation so
     * that ORM tenant filters are suppressed for the duration of the call.
     *
     * @param code template code
     * @return the platform template if found
     */
    Optional<MailTemplate> findPlatformByCode(String code);

    /**
     * Like {@link #resolve(String)} but WITHOUT the {@code isEnabled} filter —
     * for read-only authoring tools (preview, variable listing) where a
     * disabled template must stay inspectable so it can be verified before
     * being enabled. Delivery paths must keep using {@link #resolve(String)}.
     */
    MailTemplate resolveAny(String code);

    /**
     * {@link #findPlatformByCode(String)} without the {@code isEnabled}
     * filter; platform-fallback half of {@link #resolveAny(String)}.
     */
    Optional<MailTemplate> findPlatformByCodeAny(String code);

    /**
     * All rows visible to the caller for {@code code}: the caller's own row
     * plus the platform row, any enabled state — at most one per scope thanks
     * to {@code uk(tenantId, code)}. {@code @CrossTenant} in the
     * implementation; callers apply the tier policy on the result.
     */
    List<MailTemplate> findVisibleByCode(String code);

    /** All rows visible to the caller (own tenant + platform tier), any enabled state. */
    List<MailTemplate> findVisibleList();

    /**
     * The platform row with this id, if any — {@code @CrossTenant} in the
     * implementation. Backs the Customize action and the platform-row write
     * probe.
     */
    Optional<MailTemplate> findPlatformById(Long id);

    /**
     * The tenant-facing management view of the overlay: one row per visible
     * {@code code} — the caller's own row when they have one (their object of
     * management, shown even when disabled or locked out), otherwise the
     * inherited platform row. Each row carries its derived
     * {@link io.softa.starter.message.mail.enums.MailTemplateScope} and the
     * governing {@code overridable} flag.
     */
    List<MailTemplateEffectiveDTO> listEffective();

    /**
     * The templates that send-time resolution would actually pick for the
     * caller, one per code ({@link MailScope#OVERLAY} semantics) — for
     * discovery APIs listing "which codes can I send with".
     *
     * @param enabledOnly restrict to enabled rows (what {@code resolve} sees)
     */
    List<MailTemplate> resolveEffectiveList(boolean enabledOnly);

    /**
     * Copy-on-write customization: copy the platform template {@code
     * platformTemplateId} into the caller's tenant scope under the same
     * {@code code}, so the copy shadows the platform row from now on.
     * Deleting the copy reverts to the inherited platform template.
     * <p>
     * Rejected when: multi-tenancy is disabled, the caller is the platform
     * scope, the id is not a platform template, the platform template is
     * locked ({@code overridable = false}), or the code is already customized
     * in this tenant. A pinned {@code preferredServerConfigId} is carried over
     * only when the caller's scope may pin it (own config, or platform config
     * shared with tenants); otherwise the copy's pin is cleared.
     *
     * @return the id of the newly created tenant copy
     */
    Long customize(Long platformTemplateId);

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
     * listing): no code resolution, no {@code isEnabled} filter — the row
     * being edited is the row inspected. Tenant visibility follows the ORM
     * filter, i.e. exactly the rows whose edit form the caller can open.
     *
     * @throws io.softa.framework.base.exception.BusinessException if the row does not exist
     */
    MailTemplate getRequiredById(Long id);

    /** {@link #listVariables(String)} addressed by row id (editor tooling). */
    List<MailTemplateVariableDTO> listVariablesById(Long id);
}
