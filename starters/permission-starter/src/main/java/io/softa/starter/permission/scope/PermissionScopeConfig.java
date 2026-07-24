package io.softa.starter.permission.scope;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Default-bean wiring for the HR permission scope module.
 *
 * <p>{@code @ConditionalOnMissingBean} is reliable on {@code @Bean} methods
 * (evaluated at config-class processing time, after component scan has
 * discovered all explicit {@code @Component} beans of that type). The same
 * conditional on a {@code @Component} class has subtle ordering issues that
 * caused {@code DefaultDepartmentCascadePathResolver} to be silently skipped during
 * startup. That manifested as "DepartmentSubtreeScopeContributor requires a bean of
 * type DepartmentCascadePathResolver" on the first contributor injection.
 *
 * <p>Future application-level customisations: drop a {@code @Component}
 * implementation of {@link DepartmentCascadePathResolver} in the classpath — Spring
 * sees the user-provided bean first, the conditional below short-circuits,
 * and the default does not register.
 */
@Configuration
public class PermissionScopeConfig {

    @Bean
    @ConditionalOnMissingBean
    public DepartmentCascadePathResolver defaultDepartmentCascadePathResolver() {
        return new DefaultDepartmentCascadePathResolver();
    }
}
