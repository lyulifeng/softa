package io.softa.starter.metadata.ddl.introspect;

import java.util.List;

/**
 * The catalog↔physical drift found by {@link PhysicalDriftAuditor}: what the metadata declares
 * but the database physically lacks, and what the database physically carries on managed tables
 * without a declaration. Entry strings are pre-formatted for the consolidated log block
 * ({@code table.column (Model.field)} style) and for the {@code /metadata/status} endpoint.
 *
 * <p>Deliberately <b>not</b> reported: tables outside the audited model set (on a partial
 * scanner-scope or a shared database they legitimately belong to someone else), so the report
 * never cries wolf about neighbors.
 *
 * @param missingTables     declared models whose physical table is absent
 * @param missingColumns    declared stored fields whose physical column is absent (table exists)
 * @param missingIndexes    declared indexes physically absent (table exists)
 * @param undeclaredColumns physical columns on a managed table that no stored field declares
 *                          (hand-added, or orphaned by a removed field pending its warn-only DROP)
 * @param undeclaredIndexes physical indexes on a managed table that no declaration covers
 *                          (primary-key indexes excluded)
 * @param typeMismatches    columns whose physical shape differs from the declared one
 *                          ({@link PhysicalTypeCompat} verdict ≠ EQUAL) — including narrowings
 *                          whose MODIFY was deferred to manual SQL, re-surfaced here every boot
 */
public record PhysicalDriftReport(
        List<String> missingTables,
        List<String> missingColumns,
        List<String> missingIndexes,
        List<String> undeclaredColumns,
        List<String> undeclaredIndexes,
        List<String> typeMismatches) {

    public boolean isEmpty() {
        return missingTables.isEmpty() && missingColumns.isEmpty() && missingIndexes.isEmpty()
                && undeclaredColumns.isEmpty() && undeclaredIndexes.isEmpty() && typeMismatches.isEmpty();
    }

    public int total() {
        return missingTables.size() + missingColumns.size() + missingIndexes.size()
                + undeclaredColumns.size() + undeclaredIndexes.size() + typeMismatches.size();
    }
}
