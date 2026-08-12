package io.softa.starter.file.enums;

/**
 * Which of the two runs of the shared row pipeline this is.
 *
 * <p>Internal to the pipeline — it is not persisted, not part of any request, and deliberately not on
 * {@code ImportTemplateDTO}: it says what the caller asked for on THIS call, whereas the DTO carries the
 * template's stored configuration and travels over MQ.
 *
 * <p>It exists because the boolean it replaces read, at the call site, exactly like a switch that turns
 * validation off — {@code processRows(dto, data, false)}. It never was one. Both modes run every check
 * (field handlers, relation lookups, unique constraints, the custom handler); {@link #VALIDATE_ONLY} is
 * purely subtractive — it skips the writes.
 */
public enum ImportMode {

    /** Validate and then persist the rows that passed. Failed rows are reported, not written. */
    IMPORT,

    /**
     * Validate and report, write nothing. The pipeline skips persistence, and a custom handler is told
     * so it can skip its own side effects (provisioning accounts, calling out) while still contributing
     * its checks to the feedback the user sees.
     */
    VALIDATE_ONLY;

    public boolean isValidateOnly() {
        return this == VALIDATE_ONLY;
    }
}
