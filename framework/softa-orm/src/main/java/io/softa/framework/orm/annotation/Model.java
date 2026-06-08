package io.softa.framework.orm.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.StorageType;

/**
 * Marks a Java class as a Softa metadata Model.
 * <p>
 * The annotated class <b>must</b> extend
 * {@link io.softa.framework.orm.entity.AbstractModel} (typically through
 * {@link io.softa.framework.orm.entity.AuditableModel}). The {@code modelName}
 * is derived from the class simple name (no override).
 *
 * <p><b>Requires {@code metadata-starter}</b> on the classpath to take effect.
 * softa-orm defines this annotation; {@code metadata-starter} contains the
 * scanner that reads it and reconciles {@code sys_model} rows / DDL. Without
 * {@code metadata-starter}, this annotation is parsed by the compiler but
 * never consumed at runtime.
 *
 * @see Field
 * @see OptionSet
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface Model {

    /**
     * Display label; empty = humanized class name (e.g. {@code DeptInfo -> "Dept Info"})
     * as the base default, overridden per-language via the i18n translation table
     * (keyed by {@code model.{modelName}}). Maps to {@code SysModel.label}.
     */
    String label() default "";

    /** DB table name; empty = derived from {@code snake_case(modelName)}. */
    String tableName() default "";

    /** Description shown to users in Studio UI; empty = no description. */
    String description() default "";

    /** Default list-display field names; empty = framework default. */
    String[] displayName() default {};

    /** Default search field names; empty = framework default. */
    String[] searchName() default {};

    /** Default order entries, e.g. {@code "createdTime:desc"}. */
    String[] defaultOrder() default {};

    /** Soft-delete enabled. */
    boolean softDelete() default false;

    /** Soft-delete flag column (effective only when {@link #softDelete()}=true). */
    String softDeleteField() default "deleted";

    /** Active control enabled (adds 'active' column gating queries). */
    boolean activeControl() default false;

    /** Whether this is a timeline (effective-dated) model. */
    boolean timeline() default false;

    /** ID generation strategy. */
    IdStrategy idStrategy() default IdStrategy.DB_AUTO_ID;

    /** Storage backend. */
    StorageType storageType() default StorageType.RDBMS;

    /** Optimistic-lock (version column) enabled. */
    boolean versionLock() default false;

    /** Multi-tenant isolation enabled (adds 'tenantId' constraint to business tables). */
    boolean multiTenant() default false;

    /** Override default data source; empty = primary data source. */
    String dataSource() default "";

    /** Business key field names (composite supported). */
    String[] businessKey() default {};

    /** Partition field name for partitioned storage. */
    String partitionField() default "";

    /**
     * Microservice name when the application is deployed as multiple services.
     * Empty = single-service deployment (no inference, no default). Used by the
     * routing layer to dispatch model operations to the owning service.
     */
    String serviceName() default "";
}
