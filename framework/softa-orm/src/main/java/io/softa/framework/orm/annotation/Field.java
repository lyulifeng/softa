package io.softa.framework.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.MaskingType;
import io.softa.framework.orm.enums.WidgetType;

/**
 * Marks a Java field on a {@link Model}-annotated class as a Softa metadata Field.
 * <p>
 * The {@code fieldName} is derived from the Java field name (no override).
 * Only declared fields are processed—inherited audit fields from
 * {@code AuditableModel} are skipped via {@code Class.getDeclaredFields()}.
 *
 * <p><b>Requires {@code metadata-starter}</b> on the classpath to take effect
 * (see {@link Model}).
 */
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface Field {

    /** Display label; empty = use i18n key {@code field.{modelName}.{fieldName}.label}. Maps to {@code SysField.label}. */
    String label() default "";

    /** Description shown to users in Studio UI; empty = no description. */
    String description() default "";

    /**
     * Field type. Empty array = inferred from Java type via
     * single element = explicit override.
     */
    FieldType[] fieldType() default {};

    /** DB column name; empty = derived from {@code snake_case(fieldName)}. */
    String columnName() default "";

    /** Length (STRING / DECIMAL precision); 0 = scanner picks type-specific default. */
    int length() default 0;

    /** Scale (BIG_DECIMAL); 0 = scanner picks default. */
    int scale() default 0;

    /** Required (NOT NULL). Default reflects Java primitive (true) vs wrapper (false). */
    boolean required() default false;

    /** Readonly (not editable in default views). */
    boolean readonly() default false;

    /** I18n-translatable column. */
    boolean translatable() default false;

    /** Excluded from {@code copy()} operations. */
    boolean nonCopyable() default false;

    /** Excluded from default search. */
    boolean unsearchable() default false;

    /** Computed (formula) field; requires {@link #expression()}. */
    boolean computed() default false;

    /** Compute expression (AviatorScript); required when {@link #computed()}=true. */
    String expression() default "";

    /** Dynamic field (not stored). */
    boolean dynamic() default false;

    /** Encrypted at rest. */
    boolean encrypted() default false;

    /**
     * Masking strategy when rendering. Empty array = no masking;
     * single element = explicit masking type.
     */
    MaskingType[] maskingType() default {};

    /** Default value expression. */
    String defaultValue() default "";

    /**
     * Related model name (relational types). Empty = inferred from Java
     * field's POJO type's class simple name; <b>required</b> when the Java
     * type is {@code Long} (i.e. storing FK id only).
     */
    String relatedModel() default "";

    /** Related field on the related model; empty = {@code "id"}. */
    String relatedField() default "";

    /** Many-to-many join model name. */
    String joinModel() default "";

    /** Many-to-many join model's left field. */
    String joinLeft() default "";

    /** Many-to-many join model's right field. */
    String joinRight() default "";

    /** Cascaded field path, e.g. {@code "owner.name"}. */
    String cascadedField() default "";

    /** Filter expression for relational queries. */
    String filters() default "";

    /**
     * UI widget type override. Empty array = no override; the framework picks
     * the default presentation for the field's {@code fieldType} at runtime
     * (no compile-time auto-inference). Single element = explicit override,
     * e.g. {@code widgetType = WidgetType.TEXT}. AI / human writes the
     * single value directly — Java auto-wraps it into a single-element array.
     */
    WidgetType[] widgetType() default {};
}
