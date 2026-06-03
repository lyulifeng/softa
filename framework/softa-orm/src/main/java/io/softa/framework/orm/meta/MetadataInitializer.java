package io.softa.framework.orm.meta;

/**
 * SPI for components that need to initialize before {@link AppStartup} runs
 * {@code ModelManager.init()} / {@code OptionManager.init()} /
 * {@code TranslationCache.init()}.
 *
 * <p>Implementations are auto-discovered via
 * {@code @Autowired(required = false)} on {@link AppStartup} and called on the
 * boot path only.
 *
 * <p><b>Boot path only.</b> {@link AppStartup#reloadMetadata()} does
 * <em>not</em> invoke initializers—this avoids reload (triggered by broadcast
 * / cron) re-applying side effects such as DDL. If a future use case needs
 * reload-time hooks, introduce a separate {@code reloadInitializers} list
 * rather than overloading this SPI.
 *
 * <p>Currently the only known implementation is
 * {@code MetadataAnnotationScanner} (in {@code starters/metadata-starter}).
 */
public interface MetadataInitializer {

    /**
     * Called by {@link AppStartup} before the core managers are initialized.
     * <p>
     * Implementations should be idempotent—the boot path may run after a
     * scanner has already populated state in a previous lifecycle.
     */
    void initialize();
}
