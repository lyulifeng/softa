package io.softa.framework.orm.enums;

/**
 * Marks the ownership of a row — which channel controls it and how it
 * evolves.
 *
 * <p>This is a <b>generic data-ownership tag</b>, not metadata-specific. It
 * currently lives on the {@code sys_*} metadata catalog tables, but the same
 * tier model is intended for future business tables that ship
 * platform-defaults a tenant can customize (e.g. system roles, workflow
 * templates, email templates, default categories).
 *
 * <h2>Metadata catalog usage</h2>
 *
 * All tenants share the {@code sys_*} metadata — per-tenant fields / models
 * are <b>not supported</b> (product decision; revisiting it requires a new
 * architecture decision plus a tenant dimension on the {@code sys_*} unique
 * keys). On the metadata catalog only two tiers are live:
 *
 * <ul>
 *   <li>{@link #PLATFORM_MAINTAINED} — materialized from {@code @Model} /
 *   {@code @Field} / {@code @OptionSet} / {@code @OptionItem} declarations in
 *   source code. Source of truth is the Java annotation; evolves via the
 *   boot-time scanner (dev) or runtime Plan/Apply (prod).</li>
 *
 *   <li>{@link #STUDIO_MANAGED} — platform <b>no-code</b> definitions
 *   (models / fields / option sets with no Java source), authored in the
 *   Studio {@code design_*} workspace. Source of truth is the design
 *   workspace; evolves via version freeze → signed deployment envelope.
 *   The envelope write path may only touch rows of
 *   this tier; the two channels are physically partitioned at the writer.</li>
 * </ul>
 *
 * <p>{@link #PLATFORM_DEFAULT} and {@link #TENANT} are <b>retired on
 * {@code sys_*}</b>: existing rows were re-tiered by migration
 * {@code V7__ownership_studio_managed.sql}. Both values remain in the enum
 * for the original business-data intent — platform-seeded rows a tenant may
 * customize, and tenant-owned rows respectively.
 *
 * <p>The scanner's reads / writes are filtered with
 * {@code WHERE ownership = 'PLATFORM_MAINTAINED'}; the envelope write path is
 * filtered to {@code STUDIO_MANAGED}. Neither channel can clobber the other,
 * and {@code OwnershipCollisionGuard} keeps their key spaces disjoint.
 */
public enum Ownership {

    /** Platform-version-controlled; materialized from code annotations. */
    PLATFORM_MAINTAINED,

    /**
     * Platform no-code definition, version-managed by Studio and applied via
     * the signed deployment envelope.
     */
    STUDIO_MANAGED,

    /**
     * Platform-provided default that tenants may override. Reserved for
     * business-data scenarios; retired on {@code sys_*}.
     */
    PLATFORM_DEFAULT,

    /**
     * Tenant-owned data. Reserved for business-data scenarios; retired on
     * {@code sys_*}.
     */
    TENANT
}
