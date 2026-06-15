package io.softa.framework.base.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.softa.framework.base.enums.OptionItemIcon;
import io.softa.framework.base.enums.OptionItemTone;

/**
 * Marks an enum constant (within an {@link OptionSet}-annotated enum) with
 * Softa metadata OptionItem display / behavior attributes.
 * <p>
 * The {@code itemCode} is NOT controlled by this annotation—it is derived from
 * the {@link com.fasterxml.jackson.annotation.JsonValue} field value on the
 * enum class.
 *
 * <p><b>Requires {@code metadata-starter}</b> on the classpath to take effect
 * (see {@code @Model}).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface OptionItem {

    /**
     * Display label; empty = humanized enum constant name (e.g. {@code MULTI_FILE -> "Multi File"})
     * as the base default, overridden per-language via the i18n translation table.
     * Maps to {@code SysOptionItem.label}.
     */
    String label() default "";

    /** Description shown to users in Studio UI; empty = no description. */
    String description() default "";

    /** Override sequence; -1 = use {@code ordinal() + 1}. */
    int sequence() default -1;

    /** Parent item code (for hierarchical OptionSets). */
    String parentItemCode() default "";

    /** UI tone hint. Empty array = no tone; single element = explicit. */
    OptionItemTone[] itemTone() default {};

    /** UI icon hint. Empty array = no icon; single element = explicit. */
    OptionItemIcon[] itemIcon() default {};
}
