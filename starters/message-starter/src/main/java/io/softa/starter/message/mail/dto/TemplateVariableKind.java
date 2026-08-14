package io.softa.starter.message.mail.dto;

/**
 * UI-oriented classification of one template token, for variable-input UIs.
 * Deliberately local to message-starter (not the framework's
 * {@code PlaceholderKind}): the framework classifies placeholder <i>parse</i>
 * shapes, while this vocabulary classifies <i>what input surface the user
 * needs</i> — including {@link #COLLECTION}, which comes from Pebble
 * {@code {% for %}} headers rather than {@code {{ }}} placeholders.
 */
public enum TemplateVariableKind {

    /** Simple name — one text input (dotted paths and unicode names included). */
    VARIABLE,

    /** Iterated by a {@code {% for %}} loop — needs a JSON array/object value. */
    COLLECTION,

    /** Computed expression — operands must be supplied via the raw JSON object. */
    EXPRESSION,

    /** Reserved ({@code @}-prefixed) — resolved server-side, no input. */
    RESERVED_FIELD
}
