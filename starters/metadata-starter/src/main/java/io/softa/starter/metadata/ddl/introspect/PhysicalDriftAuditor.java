package io.softa.starter.metadata.ddl.introspect;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;

import io.softa.starter.metadata.ddl.SysDdlContextBuilder;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalTable;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;

/**
 * Compares a metadata baseline against a {@link PhysicalSchema} snapshot into one
 * {@link PhysicalDriftReport} — the whole-catalog physical health check, so a boot surfaces
 * every drift at once instead of failing on the first drifted statement per restart.
 *
 * <p>Pure function over its inputs; the <b>caller chooses the baseline</b>: the scanner audits
 * its in-scope from-code metadata (what it is about to enforce), the checker audits the
 * {@code sys_*} rows (the contract the runtime actually enforces in production). Only stored
 * fields expect a column ({@link SysDdlContextBuilder#isStored}); index comparison is by exact
 * name (engines that mangle names — e.g. H2's synthetic unique-index suffixes — can report
 * false pairs; MySQL/PostgreSQL keep names verbatim).
 *
 * <p>Reporting only — the auditor never renders or executes DDL. Declared-but-missing entries
 * that sit behind a metadata diff are healed by the orchestrator's physical recovery in the
 * same boot; entries with no diff (and every undeclared extra) persist until acted on.
 */
@Slf4j
public final class PhysicalDriftAuditor {

    private PhysicalDriftAuditor() {
    }

    /** Primary-key backing indexes carry engine names, not declarations — never "undeclared". */
    private static boolean isPrimaryKeyIndex(String indexNameLower) {
        return indexNameLower.equals("primary")
                || indexNameLower.startsWith("primary_key")
                || indexNameLower.endsWith("_pkey");
    }

    public static PhysicalDriftReport audit(List<SysModel> models, List<SysField> fields,
                                            List<SysModelIndex> indexes, PhysicalSchema facts) {
        Map<String, List<SysField>> fieldsByModel = fields.stream()
                .collect(Collectors.groupingBy(SysField::getModelName));
        Map<String, List<SysModelIndex>> indexesByModel = indexes.stream()
                .collect(Collectors.groupingBy(SysModelIndex::getModelName));

        List<String> missingTables = new ArrayList<>();
        List<String> missingColumns = new ArrayList<>();
        List<String> missingIndexes = new ArrayList<>();
        List<String> undeclaredColumns = new ArrayList<>();
        List<String> undeclaredIndexes = new ArrayList<>();
        List<String> typeMismatches = new ArrayList<>();

        for (SysModel model : models) {
            String table = SysDdlContextBuilder.resolveTableName(model);
            if (!facts.tableExists(table)) {
                missingTables.add(table + " (model " + model.getModelName() + ")");
                continue;
            }
            PhysicalTable physical = facts.tables().get(lower(table));
            List<SysField> storedFields = fieldsByModel.getOrDefault(model.getModelName(), List.of()).stream()
                    .filter(SysDdlContextBuilder::isStored)
                    .toList();
            Set<String> declaredColumns = storedFields.stream()
                    .map(f -> lower(SysDdlContextBuilder.resolveColumnName(f)))
                    .collect(Collectors.toSet());
            for (SysField field : storedFields) {
                String column = SysDdlContextBuilder.resolveColumnName(field);
                PhysicalColumn observed = facts.column(table, column);
                if (observed == null) {
                    missingColumns.add(table + "." + column
                            + " (" + model.getModelName() + "." + field.getFieldName() + ")");
                    continue;
                }
                PhysicalTypeCompat.Verdict verdict = PhysicalTypeCompat.compare(field, observed);
                if (verdict != PhysicalTypeCompat.Verdict.EQUAL) {
                    typeMismatches.add(table + "." + column
                            + " (" + model.getModelName() + "." + field.getFieldName() + "): "
                            + PhysicalTypeCompat.describe(field, observed)
                            + " [" + verdict.name().toLowerCase(Locale.ROOT) + "]");
                }
            }
            for (PhysicalColumn column : physical.columns().values()) {
                if (!declaredColumns.contains(lower(column.name()))) {
                    undeclaredColumns.add(table + "." + column.name());
                }
            }
            List<SysModelIndex> declaredIndexes = indexesByModel.getOrDefault(model.getModelName(), List.of());
            Set<String> declaredIndexNames = declaredIndexes.stream()
                    .map(i -> lower(i.getIndexName()))
                    .collect(Collectors.toSet());
            for (SysModelIndex index : declaredIndexes) {
                if (!facts.indexExists(table, index.getIndexName())) {
                    missingIndexes.add(table + "." + index.getIndexName());
                }
            }
            for (String indexName : physical.indexNames()) {
                if (!declaredIndexNames.contains(indexName) && !isPrimaryKeyIndex(indexName)) {
                    undeclaredIndexes.add(table + "." + indexName);
                }
            }
        }
        return new PhysicalDriftReport(missingTables, missingColumns, missingIndexes,
                undeclaredColumns, undeclaredIndexes, typeMismatches);
    }

    /**
     * One consolidated WARN for a drifted report ({@code INFO} when clean), mirroring the
     * orchestrator's deferred-DDL block style so operators see the full physical health of the
     * managed set in one place.
     */
    public static void warn(PhysicalDriftReport report, String context) {
        if (report.isEmpty()) {
            log.info("{}: physical schema is consistent with the audited metadata", context);
            return;
        }
        StringBuilder body = new StringBuilder();
        section(body, "declared but physically MISSING TABLE (recovery recreates it when the "
                + "current diff touches the model; otherwise reconcile or run DDL manually)", report.missingTables());
        section(body, "declared but physically MISSING COLUMN", report.missingColumns());
        section(body, "declared but physically MISSING INDEX", report.missingIndexes());
        section(body, "physically present but UNDECLARED COLUMN (hand-added, or pending a warn-only DROP)",
                report.undeclaredColumns());
        section(body, "physically present but UNDECLARED INDEX", report.undeclaredIndexes());
        section(body, "TYPE MISMATCH between the declared and physical shape (a narrowing MODIFY is "
                + "never auto-executed — resolve with the deferred SQL or by widening the declaration)",
                report.typeMismatches());
        log.warn("""
                {}: physical schema drift — {} finding(s) between the audited metadata and the database:
                {}""", context, report.total(), body.toString().stripTrailing());
    }

    private static void section(StringBuilder body, String title, List<String> entries) {
        if (entries.isEmpty()) {
            return;
        }
        body.append("  ").append(title).append(":\n");
        entries.forEach(entry -> body.append("    - ").append(entry).append('\n'));
    }

    private static String lower(String identifier) {
        return identifier.toLowerCase(Locale.ROOT);
    }
}
