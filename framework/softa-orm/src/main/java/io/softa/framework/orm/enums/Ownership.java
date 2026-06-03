package io.softa.framework.orm.enums;

/**
 * Marks the ownership of a row — who controls it and whether tenants may
 * modify it.
 *
 * <p>This is a <b>generic data-ownership tag</b>, not metadata-specific. It
 * currently lives on the {@code sys_*} metadata catalog tables, but the same
 * tier model is intended for future business tables that ship
 * platform-defaults a tenant can customize (e.g. system roles, workflow
 * templates, email templates, default categories).
 *
 * <h2>Why three tiers</h2>
 *
 * Data in a multi-tenant ERP splits along the boundary of <i>who maintains
 * it</i>: code-controlled platform structure, platform-seeded defaults that
 * are expected to be customized, and tenant-owned data. Distinguishing these
 * three lets the scanner / runtime merge logic act safely without erasing
 * tenant work or letting tenants break code references.
 *
 * <h2>The three values</h2>
 *
 * <ul>
 *   <li>{@link #PLATFORM_MAINTAINED} — written by {@code MetadataAnnotationScanner}
 *   from {@code @Model} / {@code @Field} / {@code @OptionSet} / {@code @OptionItem}
 *   declarations in source code. Platform-version-controlled; tenants
 *   <b>cannot</b> modify these rows.</li>
 *
 *   <li>{@link #PLATFORM_DEFAULT} — platform-seeded data that tenants
 *   <b>may</b> modify. The scanner never writes or reconciles these rows.
 *
 *   <p><b>Primary intent</b>: business data with sensible defaults — system
 *   roles, workflow templates, email templates, default categories,
 *   new-tenant initialization data — where the platform ships a starting
 *   point and tenants customize freely.
 *
 *   <p><b>Metadata side (narrow use)</b>: in {@code sys_*} this tier is used
 *   only for the special case of framework-level enums (e.g.
 *   {@code softa-base}'s {@code Language}) whose source code cannot carry
 *   {@code @OptionSet} due to module-cycle prevention. They are DML-seeded
 *   into {@code sys_option_set} so the scanner won't try to delete them, but
 *   tenants rarely modify these seeded rows directly — they add overlay
 *   items as {@code TENANT}-owned rows instead.</li>
 *
 *   <li>{@link #TENANT} — tenant-specific data, written by Studio UI / Open
 *   API. Also the default value for pre-migration / unmarked rows so
 *   pre-existing rows stay opaque to the scanner.</li>
 * </ul>
 *
 * <h2>Runtime merge rules (sys_* metadata)</h2>
 *
 * When {@code ModelManager} resolves metadata for a tenant request, it merges
 * the three populations under these rules:
 *
 * <ul>
 *   <li>{@code TENANT} rows <b>may override</b> overridable attributes on
 *   {@code PLATFORM_MAINTAINED} rows (label, required, defaultValue,
 *   validation, etc.) and on {@code PLATFORM_DEFAULT} rows (any attribute,
 *   though practical use on metadata is narrow — see above).</li>
 *
 *   <li>{@code TENANT} rows <b>may add</b> fields, option items, and views
 *   not declared at the platform level.</li>
 *
 *   <li>{@code TENANT} rows <b>cannot delete</b> {@code PLATFORM_MAINTAINED}
 *   fields or option items — that would break code references. Tenants who
 *   want a platform field hidden should mark it inactive instead.</li>
 *
 *   <li>{@code TENANT} rows <b>cannot modify</b> core attributes of
 *   {@code PLATFORM_MAINTAINED} rows ({@code fieldName} / {@code fieldType} /
 *   length-narrowing); only widening / extension is allowed (e.g. raising a
 *   length cap).</li>
 * </ul>
 *
 * <p>Business tables that adopt this enum should define their own
 * application-level merge semantics; the rules above are specific to the
 * metadata catalog.
 *
 * <p>The scanner reads / writes are filtered with
 * {@code WHERE ownership = 'PLATFORM_MAINTAINED'} so it never touches tenant
 * data or platform defaults.
 */
public enum Ownership {

    /** Platform-version-controlled; written by the annotation scanner. Tenants cannot modify. */
    PLATFORM_MAINTAINED,

    /** Platform-provided default that tenants may override. Scanner never touches. */
    PLATFORM_DEFAULT,

    /** Tenant customization; default for unmarked rows. */
    TENANT
}
