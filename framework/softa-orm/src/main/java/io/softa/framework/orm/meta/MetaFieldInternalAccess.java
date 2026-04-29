package io.softa.framework.orm.meta;

/**
 * Package-private bridge that exposes write-access to a small subset of
 * {@link MetaField} attributes for components that live outside this package.
 * <p>
 * MetaField uses {@code @Setter(AccessLevel.PACKAGE)} to lock down mutation
 * after metadata initialization. Some runtime-only attributes
 * (e.g. {@link MetaField#isAutoSequence()}) need to be written by infrastructure
 * outside the meta package — for example the sequence module's registry
 * initializer. This class is the controlled escape hatch.
 * <p>
 * Do not add general-purpose setters here. Each method should correspond to a
 * specific runtime property whose write-time invariants are documented on the
 * field itself.
 */
public final class MetaFieldInternalAccess {

    private MetaFieldInternalAccess() {}

    /**
     * Mark a field as auto-filled by the sequence service on INSERT.
     * Intended to be called once during application startup (after metadata
     * load) by the sequence module's registry initializer.
     *
     * @param metaField     target field
     * @param autoSequence  true to enable auto-fill; false to clear
     */
    public static void setAutoSequence(MetaField metaField, boolean autoSequence) {
        metaField.setAutoSequence(autoSequence);
    }
}
