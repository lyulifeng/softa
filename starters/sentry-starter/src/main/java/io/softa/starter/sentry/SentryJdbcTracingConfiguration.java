package io.softa.starter.sentry;

import javax.sql.DataSource;

import com.p6spy.engine.spy.P6DataSource;
import org.jspecify.annotations.NonNull;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Optional per-statement JDBC spans. Off by default: with the flag unset no proxy is
 * installed and the JDBC call path carries zero extra cost. When enabled, every
 * DataSource bean is wrapped in a P6Spy proxy; sentry-jdbc's listener (registered via
 * P6Spy's ServiceLoader) records a span per statement — only on requests the tracing
 * sampler actually selected, so the steady-state overhead is the proxy call itself.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "softa.sentry.jdbc-tracing.enabled", havingValue = "true")
public class SentryJdbcTracingConfiguration {

    /**
     * Static: BeanPostProcessors must be instantiated before regular beans; a static
     * bean method keeps this configuration class out of that early-init path.
     */
    @Bean
    public static BeanPostProcessor sentryJdbcDataSourceWrapper() {
        return new BeanPostProcessor() {
            @Override
            public Object postProcessAfterInitialization(@NonNull Object bean, @NonNull String beanName) {
                if (bean instanceof DataSource dataSource && !(bean instanceof P6DataSource)) {
                    return new P6DataSource(dataSource);
                }
                return bean;
            }
        };
    }
}
