package io.softa.framework.orm.sequence;

/**
 * Static accessor for the application-scoped {@link SequenceFieldRegistry},
 * intended for non-DI components like
 * {@code io.softa.framework.orm.jdbc.pipeline.DataCreatePipeline}, which is
 * {@code new}'d per insert call rather than being a Spring bean.
 *
 * <p>{@link SequenceFieldRegistry} writes itself in here at
 * {@code @PostConstruct} time. Callers must tolerate {@code null} (registry
 * not yet initialized — happens during early startup, or in tests where the
 * registry bean is not present).
 *
 * <p>Tests should reset the holder via {@link #set(SequenceFieldRegistry)}
 * with a stub instance; helper {@code @AfterEach} cleanup recommended.
 */
public final class SequenceFieldRegistryHolder {

    private static volatile SequenceFieldRegistry instance;

    private SequenceFieldRegistryHolder() {}

    /**
     * @return the registered registry, or {@code null} if not yet initialized.
     */
    public static SequenceFieldRegistry get() {
        return instance;
    }

    /**
     * Install (or replace) the registry. Called by
     * {@link SequenceFieldRegistry#registerHolder()} at startup; tests may
     * also call this to inject a stub.
     */
    public static void set(SequenceFieldRegistry registry) {
        instance = registry;
    }

    /** Reset for tests. */
    public static void clear() {
        instance = null;
    }
}
