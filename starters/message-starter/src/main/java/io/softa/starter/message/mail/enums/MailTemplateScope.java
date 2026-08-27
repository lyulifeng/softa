package io.softa.starter.message.mail.enums;

/**
 * Ownership scope of one row in the tenant-facing <b>effective</b> template
 * list (the overlay management view). Derived per {@code code} — never
 * persisted.
 */
public enum MailTemplateScope {

    /**
     * A platform template ({@code tenant_id = 0}) the caller inherits and has
     * not customized. Read-only for tenant callers — the Customize action
     * copies it into the tenant scope.
     */
    INHERITED,

    /**
     * The caller's own template shadowing a platform template with the same
     * {@code code}. Deleting it reverts to the inherited platform template.
     */
    CUSTOMIZED,

    /** The caller's own template with a code no platform template uses. */
    OWN
}
