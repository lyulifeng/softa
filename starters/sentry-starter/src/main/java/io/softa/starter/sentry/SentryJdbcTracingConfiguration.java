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
 *
 * <p><b>Spans only, never logs.</b> P6Spy ships a logging module of its own
 * (P6LogFactory), enabled by default, which would write every statement to a
 * {@code spy.log} FILE — invisible to any stdout-based log pipeline, unrotated, and
 * duplicating the SQL that the ORM already logs per request under its own debug flag.
 * Narrowing the module list to the core factory disables it. The Sentry listener is
 * unaffected: P6Spy composes its listeners from two independent sources
 * (DefaultJdbcEventListenerFactory registers module listeners AND ServiceLoader
 * listeners separately), and sentry-jdbc arrives over the latter.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnProperty(name = "softa.sentry.jdbc-tracing.enabled", havingValue = "true")
public class SentryJdbcTracingConfiguration {

    /** P6Spy reads its options from system properties under this prefix. */
    private static final String P6SPY_MODULELIST_PROPERTY = "p6spy.config.modulelist";

    /** Core proxying only — P6LogFactory (statement logging) deliberately absent. */
    private static final String CORE_MODULE_ONLY = "com.p6spy.engine.spy.P6SpyFactory";

    /**
     * Static: BeanPostProcessors must be instantiated before regular beans; a static
     * bean method keeps this configuration class out of that early-init path. It is
     * also where the module list is pinned — P6Spy resolves its options lazily on the
     * first proxied connection, which is necessarily after this runs.
     */
    @Bean
    public static BeanPostProcessor sentryJdbcDataSourceWrapper() {
        // An explicit setting (system property, spy.properties, or environment) always
        // wins: an application that deliberately configured P6Spy keeps its own setup.
        if (System.getProperty(P6SPY_MODULELIST_PROPERTY) == null) {
            System.setProperty(P6SPY_MODULELIST_PROPERTY, CORE_MODULE_ONLY);
        }
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
