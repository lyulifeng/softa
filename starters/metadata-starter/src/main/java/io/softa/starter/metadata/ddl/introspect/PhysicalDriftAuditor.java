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
 * fields expect a column ({@link SysDdlContextBuilder#isStored}); index comparison goes through
 * {@link IndexNameCompat} — the same matcher the convergence planner uses — so engine-mangled
 * names (H2's synthetic unique-index suffixes) neither report as drift nor get acted on.
 *
 * <p>Reporting only — the auditor never renders or executes DDL. Under an active
 * {@code scanner-scope} the orchestrator's convergence pass heals in-scope drift in the same
 * boot and this audit runs on the healed snapshot, so it reports the <i>residual</i>: what
 * convergence cannot own (projection tables, out-of-scope models) or could not do. Under an
 * empty scope (production) nothing heals and the report is the whole drift.
 */
@Slf4j
public final class PhysicalDriftAuditor {

    private PhysicalDriftAuditor() {
    }

    public static PhysicalDriftReport audit(List<SysModel> models, List<SysField> fields,
                                            List<SysModelIndex> indexes, PhysicalSchema facts) {
        Map<String, List<SysField>> fieldsByModel = fields.stream()
                .collect(Collectors.groupingBy(SysField::getModelName));
        Map<String, List<SysModelIndex>> indexesByModel = indexes.stream()
                .collect(Collectors.groupingBy(SysModelIndex::getModelName));

        List<String> missingTables = new ArrayList<>();
        List<String> missingProjectionTables = new ArrayList<>();
        List<String> missingColumns = new ArrayList<>();
        List<String> missingIndexes = new ArrayList<>();
        List<String> undeclaredColumns = new ArrayList<>();
        List<String> undeclaredIndexes = new ArrayList<>();
        List<String> typeMismatches = new ArrayList<>();

        for (SysModel model : models) {
            // A projection audits one-way only: what it declares must exist (missing columns /
            // type mismatches still matter for its reads), but the table's other columns and
            // indexes belong to the owner — reporting them as undeclared would flood every boot
            // with false positives, one copy per projection.
            boolean projection = Boolean.TRUE.equals(model.getProjection());
            String table = SysDdlContextBuilder.resolveTableName(model);
            if (!facts.tableExists(table)) {
                (projection ? missingProjectionTables : missingTables)
                        .add(table + " (model " + model.getModelName() + ")");
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
            if (!projection) {
                for (PhysicalColumn column : physical.columns().values()) {
                    if (!declaredColumns.contains(lower(column.name()))) {
                        undeclaredColumns.add(table + "." + column.name());
                    }
                }
            }
            List<SysModelIndex> declaredIndexes = indexesByModel.getOrDefault(model.getModelName(), List.of());
            Set<String> declaredIndexNames = declaredIndexes.stream()
                    .map(i -> lower(i.getIndexName()))
                    .collect(Collectors.toSet());
            for (SysModelIndex index : declaredIndexes) {
                if (!IndexNameCompat.declaredIndexExists(physical, index.getIndexName())) {
                    missingIndexes.add(table + "." + index.getIndexName());
                }
            }
            if (!projection) {
                for (String indexName : physical.indexNames()) {
                    if (!IndexNameCompat.matchesAnyDeclared(indexName, declaredIndexNames)
                            && !IndexNameCompat.isPrimaryKeyIndex(indexName)) {
                        undeclaredIndexes.add(table + "." + indexName);
                    }
                }
            }
        }
        return new PhysicalDriftReport(missingTables, missingProjectionTables, missingColumns, missingIndexes,
                undeclaredColumns, undeclaredIndexes, typeMismatches);
    }

    /**
     * One consolidated WARN for a drifted report ({@code INFO} when clean), mirroring the
     * orchestrator's deferred-DDL block style so operators see the full physical health of the
     * managed set in one place.
     */
    public static void warn(PhysicalDriftReport report, String context) {
        // Projection tables are the one ERROR-level finding: the model's queries WILL fail until
        // the owner (another model, or an external process such as a BI pipeline) creates the
        // table — but that creation is deliberately outside this model's control, so it is a
        // loud log, never a boot failure and never a recovery CREATE.
        if (!report.missingProjectionTables().isEmpty()) {
            StringBuilder entries = new StringBuilder();
            report.missingProjectionTables().forEach(entry -> entries.append("  - ").append(entry).append('\n'));
            log.error("""
                    {}: {} projection model(s) point at a physically MISSING table — every query on them \
                    will fail until the owning model or external process creates it:
                    {}""", context, report.missingProjectionTables().size(), entries.toString().stripTrailing());
        }
        if (report.isEmpty()) {
            log.info("{}: physical schema is consistent with the audited metadata", context);
            return;
        }
        StringBuilder body = new StringBuilder();
        section(body, "declared but physically MISSING TABLE (an active scanner-scope recreates "
                + "in-scope tables at boot; otherwise run DDL manually)", report.missingTables());
        section(body, "declared but physically MISSING COLUMN", report.missingColumns());
        section(body, "declared but physically MISSING INDEX", report.missingIndexes());
        section(body, "physically present but UNDECLARED COLUMN (an active scanner-scope drops these "
                + "on in-scope tables at boot; otherwise declare the column or DROP it manually)",
                report.undeclaredColumns());
        section(body, "physically present but UNDECLARED INDEX", report.undeclaredIndexes());
        section(body, "TYPE MISMATCH between the declared and physical shape (an active scanner-scope "
                + "converges in-scope columns to the declared shape at boot; otherwise resolve "
                + "manually or change the declaration)",
                report.typeMismatches());
        if (body.isEmpty()) {
            return;   // only projection-table findings — already reported at ERROR above
        }
        log.warn("""
                {}: physical schema drift — {} finding(s) between the audited metadata and the database:
                {}""", context, report.total() - report.missingProjectionTables().size(),
                body.toString().stripTrailing());
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
