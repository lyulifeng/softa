package io.softa.starter.tenant.entitlement;

import java.util.Set;

import org.springframework.stereotype.Component;

import io.softa.framework.orm.service.EntitlementService;

/**
 * tenant-starter implementation of the framework {@link EntitlementService} SPI. Thin adapter
 * over {@link EntitlementResolver}; registered as a bean so consumers that inject
 * {@code EntitlementService} {@code @Autowired(required = false)} (e.g. user-starter) light up
 * only when tenant-starter is on the classpath — otherwise they get {@code null} = pure RBAC.
 */
@Component
public class EntitlementServiceImpl implements EntitlementService {

    private final EntitlementResolver resolver;

    public EntitlementServiceImpl(EntitlementResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public Set<String> entitledModules(Long tenantId) {
        EntitlementInfo info = resolver.resolve(tenantId);
        Set<String> modules = info.entitledModuleIds();
        return modules != null ? modules : Set.of();
    }
}
