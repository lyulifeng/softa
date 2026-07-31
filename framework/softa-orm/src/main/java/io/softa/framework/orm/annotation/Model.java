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

    /**
     * This model's rows are partitioned by country — one independent set per country,
     * so a value domain that differs between countries is stored as separate rows
     * rather than shared ones. Queries are then narrowed to the country of the
     * company selected in the request context, automatically and for every
     * read path (list / page / count / reference lookup).
     *
     * <p>Boot-enforced: the model must carry a {@code MANY_TO_ONE} field onto
     * {@code CountryRegion} (see {@link io.softa.framework.orm.meta.ModelManager}).
     *
     * <p>Declare it <b>only</b> when the rows really are replicated per country.
     * A field merely recording <i>which country a record belongs to</i> — a legal
     * entity's country, an address's country — is not this: marking such a model
     * silently hides rows from every other country.
     */
    boolean multiCountry() default false;

    /**
     * Rows belong to one company, and reads are narrowed to the company selected
     * in the request context — automatically, on every read path.
     *
     * <p>The company is resolved from the model's own {@code MANY_TO_ONE} onto
     * {@code LegalEntity} when it has exactly one; declare {@link #companyField()}
     * instead when it is reached through another model.
     *
     * <p>Unlike {@link #multiCountry()} this is about <i>ownership</i>, not
     * replication: the rows are not copies per company, they each belong to one.
     * Do not declare it on data shared across companies — a tenant-wide code
     * table, a model whose company field merely records a preference — or every
     * other company's rows become invisible.
     */
    boolean companyScoped() default false;

    /**
     * Path to the company for {@link #companyScoped()}, when it is not a field on
     * this model. Dot-separated and resolved through {@code MANY_TO_ONE} /
     * {@code ONE_TO_ONE} hops, e.g. {@code deptId.legalEntityId} for a
     * per-department statistic that has no company of its own.
     *
     * <p>Leave unset to have it derived from this model's own reference onto
     * {@code LegalEntity}. Declaring it is required precisely when derivation
     * cannot be trusted: several relations may lead to a company, and which one
     * the rows belong to is not something to guess at.
     *
     * <p>Boot-enforced: the path must resolve and must land on
     * {@code LegalEntity}.
     */
    String companyField() default "";

    /**
     * Whether rows of this model may be duplicated via {@code copyById} /
     * {@code copyByIds} / {@code getCopyableFields}. Set {@code false} on
     * runtime / log models (execution traces, send records, histories) that
     * have no duplicate scenario: the copy APIs then reject the model and the
     * UI hides the Duplicate action (exposed as {@code SysModel.copyable}).
     */
    boolean copyable() default true;

    /** Override default data source; empty = primary data source. */
    String dataSource() default "";

    /** Business key field names (composite supported). */
    String[] businessKey() default {};

    /**
     * The single immediately-prior model name for a declared rename (empty = no rename).
     * Lets the metadata diff pair this model with it and emit {@code RENAME TABLE} (data preserved)
     * instead of drop+add. <b>Single-step</b> (no chain): multi-version lineage is not accumulated here —
     * studio derives it from snapshot history, the annotation lane handles a skipped version via manual
     * migration. Materialized into {@code sys_model.renamedFrom}. Replaces the retired {@code @RenamedFrom}
     * annotation.
     */
    String renamedFrom() default "";

    /** Partition field name for partitioned storage. */
    String partitionField() default "";
}
