package io.softa.framework.orm.meta;

import java.util.List;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.jdbc.CannotGetJdbcConnectionException;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

/**
 * Load the global system cache, using the highest priority Runner.
 *
 * <ul>
 *   <li>{@link #afterPropertiesSet()} (boot path) runs {@link MetadataInitializer}
 *       pre-initializers before {@link #initManagers()}.</li>
 *   <li>{@link #reloadMetadata()} (reload path; triggered by broadcast / cron)
 *       only runs {@link #initManagers()}—skipping pre-initializers prevents
 *       reload from re-applying side effects such as DDL.</li>
 * </ul>
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AppStartup implements InitializingBean {

    @Autowired
    private ModelManager modelManager;

    @Autowired
    private OptionManager optionManager;

    @Autowired
    private TranslationCache translationCache;

    /**
     * Boot-time pre-initializers (e.g., {@code MetadataAnnotationScanner} in
     * when {@code scanner-scope} is non-empty). {@code required = false} keeps the SPI optional.
     */
    @Autowired(required = false)
    private List<MetadataInitializer> preInitializers = List.of();

    /**
     * Boot path: run pre-initializers, then init the core managers.
     */
    @Override
    public void afterPropertiesSet() {
        runPreInitializers();
        initManagers();
    }

    /**
     * Reload path: re-init core managers only. Pre-initializers are skipped
     * to avoid re-applying side effects (e.g., DDL).
     */
    @Async
    public void reloadMetadata() {
        initManagers();
    }

    private void runPreInitializers() {
        preInitializers.forEach(MetadataInitializer::initialize);
    }

    private void initManagers() {
        try {
            // 1. init model manager
            modelManager.init();
            // 2. init option manager
            optionManager.init();
            // 3. init meta cache
            translationCache.init();
        } catch (CannotGetJdbcConnectionException e) {
            throw new RuntimeException("Database connection failed, please check the database configuration.", e);
        }
    }
}
