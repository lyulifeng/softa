package io.softa.starter.message.sms.service.impl;

import java.util.Map;
import java.util.Optional;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MessageScope;
import io.softa.framework.base.placeholder.PlaceholderUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.message.shared.TenantScopes;
import io.softa.starter.message.sms.entity.SmsTemplate;
import io.softa.starter.message.sms.service.SmsTemplateService;

/**
 * Implementation of {@link SmsTemplateService}.
 * <p>
 * Template resolution prefers a tenant template, falling back to the platform
 * template (tenant_id = -1). The platform lookup runs inside
 * {@link #findPlatformByCode}, annotated {@code @CrossTenant} to bypass ORM
 * tenant isolation; it is called through the Spring proxy via the {@code self}
 * reference to ensure the AOP advice is applied.
 */
@Service
public class SmsTemplateServiceImpl extends EntityServiceImpl<SmsTemplate, Long>
        implements SmsTemplateService {

    /**
     * Self-reference to allow {@code @CrossTenant} AOP advice to be applied
     * when calling {@link #findPlatformByCode} from within the same bean.
     */
    @Lazy
    @Autowired
    private SmsTemplateService self;

    @Override
    public SmsTemplate resolve(String code) {
        return resolve(code, MessageScope.TENANT);
    }

    @Override
    public SmsTemplate resolve(String code, MessageScope scope) {
        Optional<SmsTemplate> result;
        if (scope == MessageScope.PLATFORM && TenantScopes.multiTenancyEnabled()) {
            result = self.findPlatformByCode(code);
        } else {
            // TENANT (and any scope with multi-tenancy off): the current
            // scope's own rows via the plain ORM tenant filter.
            // active = true is appended by the framework's active control.
            result = searchOne(new Filters().eq(SmsTemplate::getCode, code));
        }
        return result.orElseThrow(() -> new BusinessException(
                "No SMS template found for code ''{0}''.", code));
    }

    @Override
    @CrossTenant
    public Optional<SmsTemplate> findPlatformByCode(String code) {
        return searchOne(new Filters()
                .eq(SmsTemplate::getTenantId, TenantScopes.PLATFORM)
                .eq(SmsTemplate::getCode, code));
    }

    @Override
    public String renderContent(SmsTemplate template, Map<String, Object> variables) {
        return PlaceholderUtils.replacePlaceholders(template.getContent(), variables);
    }
}
