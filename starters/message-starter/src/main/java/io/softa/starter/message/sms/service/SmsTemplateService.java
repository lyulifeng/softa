package io.softa.starter.message.sms.service;

import java.util.Map;
import java.util.Optional;

import io.softa.framework.base.message.MessageScope;
import io.softa.framework.orm.service.EntityService;
import io.softa.starter.message.sms.entity.SmsTemplate;

/**
 * CRUD and resolution service for {@link SmsTemplate}.
 * <p>
 * Template resolution prefers a tenant template, falling back to the
 * platform-defined default:
 * <pre>
 *   scope = TENANT → the current scope's own template; scope = PLATFORM →
 *   the platform tier (tenant_id = -1). No cross-tier fallback.
 * </pre>
 */
public interface SmsTemplateService extends EntityService<SmsTemplate, Long> {

    /**
     * Resolve the matching template for {@code code} in the current tenant,
     * falling back to the platform template.
     *
     * @param code template code, e.g. {@code "VERIFY_CODE"}
     * @return the resolved template
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    SmsTemplate resolve(String code);

    /**
     * Resolve the matching <b>active</b> template for {@code code} in the
     * tier the policy names — {@code TENANT} reads the current scope's own
     * rows, {@code PLATFORM} explicitly reads the platform tier (tenant_id =
     * -1) regardless of the ambient context. No cross-tier fallback.
     *
     * @throws io.softa.framework.base.exception.BusinessException if no template is found
     */
    SmsTemplate resolve(String code, MessageScope scope);

    /**
     * Query the platform-tier (tenant_id = -1) template, bypassing tenant
     * isolation.
     *
     * @param code template code
     * @return the platform template if found
     */
    Optional<SmsTemplate> findPlatformByCode(String code);

    /**
     * Render the content of {@code template} by substituting {@code variables}
     * into {@code {{ key }}} placeholders.
     */
    String renderContent(SmsTemplate template, Map<String, Object> variables);
}
