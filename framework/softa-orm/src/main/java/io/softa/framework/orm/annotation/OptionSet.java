package io.softa.framework.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a Java {@code enum} as a Softa metadata OptionSet.
 * <p>
 * The {@code optionSetCode} is derived from the enum's simple name (no override).
 * Each enum constant becomes a {@code SysOptionItem} row; its {@code itemCode}
 * is taken from the value of the enum field annotated with
 * {@link com.fasterxml.jackson.annotation.JsonValue} (fallback to
 * {@code enumConstant.name()}).
 *
 * <p><b>Requires {@code metadata-starter}</b> on the classpath to take effect
 * (see {@link Model}).
 *
 * @see OptionItem
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionSet {

    /**
     * Display label; empty = humanized enum simple name (e.g. {@code TenantStatus -> "Tenant Status"})
     * as the base default, overridden per-language via the i18n translation table
     * (keyed by {@code optionSet.{optionSetCode}}). Maps to {@code SysOptionSet.label}.
     */
    String label() default "";

    /** Description shown to users in Studio UI; empty = no description. */
    String description() default "";
}
