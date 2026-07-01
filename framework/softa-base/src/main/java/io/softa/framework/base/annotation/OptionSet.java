package io.softa.framework.base.annotation;

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
 * (see {@code @Model}).
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

    /**
     * The single immediately-prior option-set code for a declared rename (empty = no rename;
     * single-step). Materialized into {@code sys_option_set.renamedFrom}; lets the diff pair this option
     * set with it instead of drop+add.
     */
    String renamedFrom() default "";
}
