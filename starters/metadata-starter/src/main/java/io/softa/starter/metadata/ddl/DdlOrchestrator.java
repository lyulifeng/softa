package io.softa.starter.metadata.ddl;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.BadSqlGrammarException;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.framework.base.utils.StringTools;
import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.jdbc.database.DBUtil;
import io.softa.starter.metadata.ddl.DdlPolicy.ModelOps;
import io.softa.starter.metadata.ddl.context.ModelDdlCtx;
import io.softa.starter.metadata.ddl.dialect.DdlDialect;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchemaReader;
import io.softa.starter.metadata.ddl.introspect.PhysicalTypeCompat;
import io.softa.starter.metadata.ddl.spi.DdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;
import io.softa.starter.metadata.scanner.diff.DiffEngine;
import io.softa.starter.metadata.scanner.diff.SchemaDiff;

/**
 * Applies a {@link SchemaDiff} to the database by rendering and executing the
 * appropriate DDL through the dialect-specific {@link DdlDialect}, gated by
 * {@link DdlPolicy}.
 *
 * <p>DDL auto-execute policy:
 * <ul>
 *   <li>CREATE TABLE / ADD COLUMN / MODIFY COLUMN / CHANGE COLUMN (declared
 *       rename) / RENAME TABLE (declared rename) → execute</li>
 *   <li>DROP TABLE / DROP COLUMN / DROP INDEX → never execute; all warn-only
 *       units are collected into a single consolidated WARN whose body is one
 *       copy-paste SQL block (labels ride along as {@code --} comments)</li>
 *   <li>undeclared {@code tableName}-attribute change → warn-only RENAME hint</li>
 * </ul>
 *
 * <p><b>Granularity</b>: every change renders as its own {@link RenderedDdl} and
 * executes <b>one statement at a time</b> ({@link SqlStatements}). This is a
 * correctness constraint, not a style choice: (a) MySQL Connector/J rejects
 * multi-statement strings without {@code allowMultiQueries=true}; (b) the
 * "already applied" degradation below classifies per statement — batching N
 * changes into one statement (or N statements into one execute) lets a
 * duplicate on the first change silently swallow the remaining N-1, after
 * which the committed {@code sys_*} rows make the diff empty and the loss
 * permanent.
 *
 * <p><b>Renames</b> (the {@code renamedFrom} attribute): when declared, the
 * upstream {@link DiffEngine} pairs the removed-old / added-new split into a single
 * {@code Modification(kind=RENAME)}, which this orchestrator renders as
 * {@code CHANGE COLUMN old new ...} (field, kind {@code DECLARED_COLUMN_RENAME}) or
 * {@code RENAME TABLE old TO new} (model, kind {@code DECLARED_TABLE_RENAME}) and
 * <b>auto-executes</b> — the data is preserved in place. Without a declaration the
 * diff still sees {@code added=[new] + removed=[old]} and processes it as ADD COLUMN
 * (auto) + DROP COLUMN (warn-only) — the old column keeps its data, the new is NULL;
 * to rename safely either declare {@code renamedFrom} or pre-stage an explicit
 * {@code CHANGE COLUMN} migration + matching {@code UPDATE sys_field} rows. See
 * {@code annotation-lane.md} Scenario 10 for the workflow.
 *
 * <p>Idempotency: relies on the {@link SchemaDiff} being accurate. If diff
 * says "field added" but the column already exists, the dialect will fail
 * with SQL error 1060 (Duplicate column) on MySQL — caught and degraded to
 * WARN for that statement only (assumes manual run of equivalent SQL already
 * happened); the remaining statements still execute.
 *
 * <p><b>Physical recovery</b>: the diff is
 * computed against {@code sys_*}, which a hand-touched database can leave out of step with the
 * physical schema — a planned MODIFY can target a hand-dropped column, planned ALTERs a
 * hand-dropped table, a planned CREATE a pre-existing table. Before rendering, the orchestrator
 * snapshots the managed tables ({@link PhysicalSchemaReader}) and prepends <b>additive-only</b>
 * recovery units (labelled {@code [physical-recovery]}): the missing column / table is
 * recreated from the code definition, a pre-existing table is adopted column-by-column. The
 * originally planned statements always still render <i>after</i> the recovery unit — on a true
 * recovery they re-assert as no-ops or degrade as already-applied, and when the introspection
 * itself was stale they carry the real change — so wrong facts can only add WARN noise, never
 * lose a change. Introspection failure logs a WARN and disables recovery for that run (the
 * planning then runs purely from the metadata diff — no knob, graceful degradation only).
 *
 * <p>Failure handling: non-degradable SQL errors propagate as runtime
 * exceptions, which surface in {@code MetadataAnnotationScanner.initialize()}
 * and fail the {@code AppStartup} sequence (fail-fast while the scanner is
 * active). Because the scanner runs DDL <b>before</b> committing the
 * {@code sys_*} rows, a failed boot leaves the catalog rows unwritten — the
 * next boot recomputes the same diff and retries; DDL that already succeeded
 * on the earlier attempt degrades to WARN via the already-applied
 * classification above.
 */
@Slf4j
public class DdlOrchestrator {

    /** Label marker on units the physical-recovery planning prepended. */
    static final String RECOVERY_TAG = "[physical-recovery]";

    private final JdbcTemplate jdbcTemplate;
    private final DdlMetadataResolver metadataResolver;
    private final String datasourceUrl;

    public DdlOrchestrator(JdbcTemplate jdbcTemplate,
                           DdlMetadataResolver metadataResolver,
                           @Value("${spring.datasource.url:}") String datasourceUrl) {
        this.jdbcTemplate = jdbcTemplate;
        this.metadataResolver = metadataResolver;
        this.datasourceUrl = datasourceUrl;
    }

    /** Compatibility overload without from-code indexes (physical table recovery then recreates without them). */
    public void apply(SchemaDiff diff, List<SysModel> allCodeModels, List<SysField> allCodeFields) {
        apply(diff, allCodeModels, allCodeFields, List.of());
    }

    /** Self-introspecting overload: snapshots the managed tables itself (skipped on an empty diff). */
    public void apply(SchemaDiff diff, List<SysModel> allCodeModels, List<SysField> allCodeFields,
                      List<SysModelIndex> allCodeIndexes) {
        apply(diff, allCodeModels, allCodeFields, allCodeIndexes,
                diff.isEmpty() ? null : introspect(allCodeModels));
    }

    /**
     * Apply the diff. Called by {@code MetadataAnnotationScanner.initialize()}
     * <b>before</b> {@code SysJdbcWriter.apply(diff)} writes the {@code sys_*}
     * rows, so that a DDL failure leaves the catalog rows unwritten and the
     * next boot retries the same diff.
     *
     * @param diff           the computed schema diff
     * @param allCodeModels  all from-code {@code SysModel}s — used by
     *                       {@link DdlPolicy} to resolve model attributes (e.g.
     *                       custom {@code tableName}) when a model has field/index
     *                       changes but no model-level diff
     * @param allCodeFields  all from-code {@code SysField}s — used to build a
     *                       complete field→column mapping for index DDL, so that
     *                       indexes referencing pre-existing fields with custom
     *                       {@code columnName} are resolved correctly
     * @param allCodeIndexes all from-code {@code SysModelIndex}es — used to
     *                       recreate the full index set when physical recovery
     *                       rebuilds a hand-dropped table
     * @param facts          the physical snapshot to plan recovery against, or
     *                       {@code null} to plan purely from the metadata diff —
     *                       the scanner shares its audit snapshot here
     */
    public void apply(SchemaDiff diff, List<SysModel> allCodeModels, List<SysField> allCodeFields,
                      List<SysModelIndex> allCodeIndexes, PhysicalSchema facts) {
        List<RenderedDdl> rendered = render(diff, allCodeModels, allCodeFields, allCodeIndexes, facts);
        long recovered = rendered.stream().filter(d -> d.label().contains(RECOVERY_TAG)).count();
        if (recovered > 0) {
            log.warn("DdlOrchestrator: physical schema drift detected — {} additive recovery unit(s) "
                    + "prepended (see the {} labels below)", recovered, RECOVERY_TAG);
        }
        ExecResult result = executeAll(rendered);
        warnDeferred(result.deferred());
        log.info("DdlOrchestrator: executed {} DDL statement(s), skipped {} already applied; "
                        + "{} drop/rename/narrowing operation(s) deferred to manual SQL",
                result.executed(), result.skipped(), result.deferred().size());
    }

    /** Outcome of one execution pass over rendered units. */
    private record ExecResult(int executed, int skipped, List<RenderedDdl> deferred) {
    }

    /**
     * Execute the auto kinds statement by statement, collecting warn-only units. Shared by the
     * diff-driven {@link #apply} and the snapshot-driven {@link #reconcilePhysical} so both
     * paths get the same already-applied degradation and CREATE-degrade short-circuit.
     */
    private ExecResult executeAll(List<RenderedDdl> rendered) {
        int executed = 0;
        int skipped = 0;
        List<RenderedDdl> deferred = new ArrayList<>();
        for (RenderedDdl ddl : rendered) {
            if (!ddl.autoExecute()) {
                deferred.add(ddl);
                continue;
            }
            boolean firstStatement = true;
            for (String statement : ddl.statements()) {
                boolean ran = executeStatement(ddl.kind(), ddl.label(), statement);
                if (ran) {
                    executed++;
                } else {
                    skipped++;
                }
                // A degraded CREATE TABLE means the table pre-exists with an unknown shape: the
                // unit's remaining statements (PostgreSQL renders COMMENT ON ... separately) may
                // reference columns the physical table lacks — skip them. Adoption / recovery
                // units carry their own comments for whatever they actually add. Index-rebuild
                // units keep running after a degraded DROP half (the ADD half must still apply).
                if (firstStatement && !ran && ddl.kind() == RenderedDdl.Kind.CREATE_TABLE) {
                    break;
                }
                firstStatement = false;
            }
        }
        return new ExecResult(executed, skipped, deferred);
    }

    /**
     * Snapshot the managed tables' physical shape for recovery planning — also called by the
     * scanner so one snapshot serves both the drift audit and the recovery. Any failure
     * (introspection is an optimization, never a gate) degrades to "no facts" — the
     * render then plans purely from the metadata diff, the pre-introspection behavior.
     */
    public PhysicalSchema introspect(List<SysModel> allCodeModels) {
        DataSource dataSource = jdbcTemplate.getDataSource();
        if (dataSource == null) {
            return null;
        }
        try {
            return PhysicalSchemaReader.readManagedTables(dataSource, allCodeModels);
        } catch (Exception e) {
            log.warn("DdlOrchestrator: physical schema introspection unavailable — recovery planning "
                    + "disabled for this run: {}", e.getMessage());
            return null;
        }
    }

    /** Label marker on units planned by {@link #reconcilePhysical} (the catalog-table boot path). */
    static final String CATALOG_TAG = "[catalog]";

    /**
     * Converge the given models' physical tables <b>directly from their from-code
     * definitions</b> against the introspected facts — no catalog-row diff involved.
     *
     * <p>This is the boot path for the {@code sys_*} catalog tables themselves, whose
     * "last applied state" is recorded nowhere but the physical schema (the rows that
     * record every other model's state live <i>inside</i> these tables — the
     * chicken-and-egg that used to require a hand-written migration for every
     * catalog-table column, and a baseline SQL file for a fresh database). It runs
     * <b>before</b> {@code SysJdbcLoader}'s strict read, so after it returns the read
     * set is structurally guaranteed to be ⊆ the physical column set.
     *
     * <p>Same policy vocabulary as the diff-driven lane, applied to the
     * annotation-vs-physical set difference:
     * <ul>
     *   <li>table missing → {@code CREATE TABLE} from the full code definition (genesis);</li>
     *   <li>column missing → {@code ADD COLUMN}; if the field declares
     *       {@code renamedFrom} and the prior column physically exists →
     *       {@code CHANGE COLUMN} instead (data carried, not divorced);</li>
     *   <li>column present but physically narrower than declared
     *       ({@link PhysicalTypeCompat.Verdict#WIDEN}) → {@code MODIFY COLUMN}
     *       (widen freely);</li>
     *   <li>column present and EQUAL → silence; NARROW / INCOMPARABLE → <b>no unit at
     *       all</b> — with no planned change there is nothing to defer, and the
     *       consolidated physical drift audit is the reporting channel;</li>
     *   <li>undeclared physical columns → never dropped, never touched (the drift
     *       audit names them);</li>
     *   <li>declared index missing → {@code ADD INDEX}.</li>
     * </ul>
     *
     * <p>Fail-fast: a declared rename whose old <b>and</b> new columns both physically
     * exist is a half-applied rename this planner must not guess about — boot fails
     * with instructions. Genuine DDL errors propagate exactly like {@link #apply}
     * (rows are never involved here, so a retry on next boot converges naturally).
     *
     * @return whether any statement was actually executed (callers refresh their
     *         snapshot only then)
     */
    public boolean reconcilePhysical(List<SysModel> models, List<SysField> fields,
                                     List<SysModelIndex> indexes, PhysicalSchema facts) {
        DdlDialect dialect = resolveDialect();
        List<RenderedDdl> out = new ArrayList<>();
        for (SysModel model : models) {
            List<SysField> modelFields = fields.stream()
                    .filter(f -> model.getModelName().equals(f.getModelName())).toList();
            List<SysModelIndex> modelIndexes = indexes.stream()
                    .filter(i -> model.getModelName().equals(i.getModelName())).toList();
            planPhysicalConvergence(dialect, model, modelFields, modelIndexes, facts, out);
        }
        if (out.isEmpty()) {
            log.info("DdlOrchestrator: {} {} table(s) physically in sync", CATALOG_TAG, models.size());
            return false;
        }
        ExecResult result = executeAll(out);
        warnDeferred(result.deferred());
        log.info("DdlOrchestrator: {} reconciled {} table(s) — executed {} DDL statement(s), "
                        + "skipped {} already applied",
                CATALOG_TAG, models.size(), result.executed(), result.skipped());
        return result.executed() > 0;
    }

    private void planPhysicalConvergence(DdlDialect dialect, SysModel model,
                                         List<SysField> modelFields, List<SysModelIndex> modelIndexes,
                                         PhysicalSchema facts, List<RenderedDdl> out) {
        String table = effectiveTableName(model);
        if (!facts.tableExists(table)) {
            ModelDdlCtx ctx = SysDdlContextBuilder.forCreate(model, modelFields, modelIndexes);
            if (ctx.getCreatedFields().isEmpty()) {
                log.warn("DdlOrchestrator: {} table {} is missing but the code definition has no stored "
                        + "fields to create it from", CATALOG_TAG, table);
                return;
            }
            out.add(RenderedDdl.of(RenderedDdl.Kind.CREATE_TABLE,
                    "CREATE TABLE " + ctx.getTableName() + " " + CATALOG_TAG + " genesis",
                    dialect.createTableDDL(ctx).toString()));
            return;
        }
        for (SysField field : modelFields) {
            if (!SysDdlContextBuilder.isStored(field)) {
                continue;
            }
            String column = effectiveColumnName(field);
            boolean columnExists = facts.columnExists(table, column);
            // renamedFrom names the prior FIELD name; catalog entity fields never declare a
            // custom columnName, so the prior column is its snake_case derivation.
            String oldColumn = StringUtils.isBlank(field.getRenamedFrom())
                    ? null : StringTools.toUnderscoreCase(field.getRenamedFrom());
            boolean oldExists = oldColumn != null && facts.columnExists(table, oldColumn);
            if (columnExists && oldExists) {
                throw new IllegalStateException(String.format(
                        "Half-applied catalog column rename on %s: both '%s' (declared renamedFrom) and "
                                + "'%s' physically exist. Resolve manually — carry the data into '%s', then "
                                + "DROP COLUMN %s — and boot again.",
                        table, oldColumn, column, column, oldColumn));
            }
            if (!columnExists) {
                if (oldExists) {
                    addIfRendered(out, renderedFieldChange(dialect,
                            SysDdlContextBuilder.forAlter(model, List.of(), List.of(),
                                    List.of(new DdlPolicy.FieldRename(field, oldColumn)), List.of()),
                            RenderedDdl.Kind.DECLARED_COLUMN_RENAME,
                            "CHANGE COLUMN " + oldColumn + " -> " + columnLabel(model, field)
                                    + " " + CATALOG_TAG + " declared renamedFrom"));
                } else {
                    addIfRendered(out, renderedFieldChange(dialect,
                            SysDdlContextBuilder.forAlter(model, List.of(field), List.of(), List.of(), List.of()),
                            RenderedDdl.Kind.ALTER_TABLE,
                            "ADD COLUMN " + columnLabel(model, field) + " " + CATALOG_TAG));
                }
                continue;
            }
            PhysicalSchema.PhysicalColumn observed = facts.column(table, column);
            // WIDEN as a positive TRIGGER (stricter than the diff lane, where the verdict only
            // vetoes an already-planned MODIFY): plan one only when executing it observably
            // changes the physical shape. A declared-unbounded column (TEXT/JSON/DTO) is
            // excluded — engines report its width inconsistently (H2-MySQL: VARCHAR(1e9)),
            // so a width-based trigger there would re-plan the same MODIFY on every boot.
            if (isBoundedDeclaredWidth(field)
                    && PhysicalTypeCompat.compare(field, observed) == PhysicalTypeCompat.Verdict.WIDEN) {
                addIfRendered(out, renderedFieldChange(dialect,
                        SysDdlContextBuilder.forAlter(model, List.of(), List.of(field), List.of(), List.of()),
                        RenderedDdl.Kind.ALTER_TABLE,
                        "MODIFY COLUMN " + columnLabel(model, field) + " " + CATALOG_TAG + " widen: "
                                + PhysicalTypeCompat.describe(field, observed)));
            }
        }
        Map<String, String> fieldToColumn = new HashMap<>();
        for (SysField field : modelFields) {
            fieldToColumn.put(field.getFieldName(), effectiveColumnName(field));
        }
        for (SysModelIndex index : modelIndexes) {
            if (!facts.indexExists(table, index.getIndexName())) {
                ModelDdlCtx ctx = SysDdlContextBuilder.forIndexChanges(model, fieldToColumn,
                        List.of(index), List.of(), List.of());
                if (ctx.isHasIndexChanges()) {
                    String sql = dialect.alterIndexDDL(ctx).toString().trim();
                    if (!sql.isEmpty()) {
                        out.add(RenderedDdl.of(RenderedDdl.Kind.ALTER_INDEX,
                                "ADD INDEX " + index.getIndexName() + " " + CATALOG_TAG, sql));
                    }
                }
            }
        }
    }

    /** Render one field-change unit, or {@code null} when the ctx carries no stored change. */
    private RenderedDdl renderedFieldChange(DdlDialect dialect, ModelDdlCtx ctx,
                                            RenderedDdl.Kind kind, String label) {
        if (!ctx.isHasAlterTableChanges()) {
            return null;
        }
        String sql = dialect.alterTableDDL(ctx).toString().trim();
        return sql.isEmpty() ? null : RenderedDdl.of(kind, label, sql);
    }

    private static void addIfRendered(List<RenderedDdl> out, RenderedDdl unit) {
        if (unit != null) {
            out.add(unit);
        }
    }

    /** Whether the declared physical shape carries a real width bound (see the WIDEN trigger note). */
    private static boolean isBoundedDeclaredWidth(SysField field) {
        var physical = SysDdlContextBuilder.resolvePhysicalFieldType(field);
        return physical != io.softa.framework.orm.enums.FieldType.TEXT
                && physical != io.softa.framework.orm.enums.FieldType.JSON
                && physical != io.softa.framework.orm.enums.FieldType.DTO;
    }

    /**
     * One consolidated WARN for all warn-only units — the body is a single
     * copy-paste SQL block, each unit's label carried as a {@code --} comment
     * line so the block stays a valid SQL script.
     */
    private void warnDeferred(List<RenderedDdl> deferred) {
        if (deferred.isEmpty()) {
            return;
        }
        String block = deferred.stream()
                .map(ddl -> "-- " + ddl.label() + "\n" + ddl.sql())
                .collect(Collectors.joining("\n\n"));
        log.warn("""
                DdlOrchestrator: {} operation(s) not auto-executed (data-bearing changes: DROP / RENAME / narrowing MODIFY).
                To apply manually:
                {}""", deferred.size(), block.indent(4).stripTrailing());
    }

    /**
     * Render the DDL for a diff <b>without executing anything</b> — the render step behind
     * {@link #apply} (which then executes the auto kinds). Returns units in execution
     * order: table renames first (declared → auto RENAME TABLE, undeclared → warn),
     * then per-model CREATE, per-change ALTERs (column adds / modifies / declared
     * renames, then index adds / rebuilds) and per-model DROP hints (warn).
     */
    private List<RenderedDdl> render(SchemaDiff diff, List<SysModel> allCodeModels, List<SysField> allCodeFields,
                                     List<SysModelIndex> allCodeIndexes, PhysicalSchema facts) {
        if (diff.isEmpty()) {
            return List.of();
        }
        Map<String, SysModel> modelsByName = allCodeModels.stream()
                .collect(Collectors.toMap(SysModel::getModelName, Function.identity(), (a, b) -> a));
        // field→column lookup grouped by modelName, for index column resolution
        Map<String, Map<String, String>> fieldToColumnByModel = allCodeFields.stream()
                .filter(f -> f.getFieldName() != null && f.getColumnName() != null)
                .collect(Collectors.groupingBy(SysField::getModelName,
                        Collectors.toMap(SysField::getFieldName, SysField::getColumnName, (a, b) -> a)));
        // TO_ONE FK physical types are resolved at reconciliation time (ReferenceColumnResolver
        // stamps relatedFieldType + length/scale onto sys_field) and read straight from the field
        // ctx here — no cross-model lookup at render.

        DdlDialect dialect = resolveDialect();
        List<DdlPolicy.ModelOps> ops = DdlPolicy.classify(diff, modelsByName);
        List<RenderedDdl> out = new ArrayList<>();
        renderTableRenames(diff, out);
        for (ModelOps op : ops) {
            Map<String, String> modelFieldToColumn =
                    fieldToColumnByModel.getOrDefault(op.model().getModelName(), Map.of());
            // Physical recovery: ALTERs against a hand-dropped table would all fail on
            // "unknown table" — recreate it from the full code definition first. The planned
            // ALTERs still render below: on the fresh table they re-assert as no-ops /
            // already-applied WARNs, and they carry the real change if the facts were stale.
            boolean tableMissing = facts != null && !facts.tableExists(effectiveTableName(op.model()));
            switch (op.operation()) {
                case CREATE_TABLE -> renderCreate(dialect, op, facts, out);
                case ALTER_TABLE -> {
                    if (tableMissing) {
                        renderRecoveredCreate(dialect, op.model(), allCodeFields, allCodeIndexes, out);
                    }
                    renderAlter(dialect, op, modelFieldToColumn, facts, out);
                }
                case ALTER_TABLE_WITH_DROP_WARNING -> {
                    if (tableMissing) {
                        renderRecoveredCreate(dialect, op.model(), allCodeFields, allCodeIndexes, out);
                    }
                    renderAlter(dialect, op, modelFieldToColumn, facts, out);
                    renderDropColumn(dialect, op, out);
                    renderDropIndex(dialect, op, out);
                }
                case DROP_TABLE_WARNING -> renderDropTable(dialect, op, out);
            }
        }
        return out;
    }

    /**
     * Physical recovery for a hand-dropped table behind planned ALTERs: recreate it from the
     * complete from-code definition (all of the model's fields + indexes, not just the diff'd
     * ones), so the subsequent ALTER units land on a fully-shaped table.
     */
    private void renderRecoveredCreate(DdlDialect dialect, SysModel model,
                                       List<SysField> allCodeFields, List<SysModelIndex> allCodeIndexes,
                                       List<RenderedDdl> out) {
        List<SysField> modelFields = allCodeFields.stream()
                .filter(f -> model.getModelName().equals(f.getModelName())).toList();
        List<SysModelIndex> modelIndexes = allCodeIndexes.stream()
                .filter(i -> model.getModelName().equals(i.getModelName())).toList();
        ModelDdlCtx ctx = SysDdlContextBuilder.forCreate(model, modelFields, modelIndexes);
        if (ctx.getCreatedFields().isEmpty()) {
            log.warn("DdlOrchestrator: table {} is physically missing but the code definition has no "
                            + "stored fields to recreate it from — the planned ALTERs will fail",
                    effectiveTableName(model));
            return;
        }
        String sql = dialect.createTableDDL(ctx).toString();
        out.add(RenderedDdl.of(RenderedDdl.Kind.CREATE_TABLE,
                "CREATE TABLE " + ctx.getTableName() + " " + RECOVERY_TAG + " table missing, recreated from code",
                sql));
    }

    // ---- per-operation rendering --------------------------------------

    private void renderCreate(DdlDialect dialect, ModelOps op, PhysicalSchema facts, List<RenderedDdl> out) {
        ModelDdlCtx ctx = SysDdlContextBuilder.forCreate(
                op.model(), op.createFields(), op.createIndexes());
        if (ctx.getCreatedFields().isEmpty()) {
            log.debug("DdlOrchestrator: skipping CREATE TABLE for {} (no stored fields)",
                    op.model().getModelName());
            return;
        }
        String sql = dialect.createTableDDL(ctx).toString();
        out.add(RenderedDdl.of(RenderedDdl.Kind.CREATE_TABLE, "CREATE TABLE " + ctx.getTableName(), sql));
        renderAdoptedTableChanges(dialect, op, facts, out);
    }

    /**
     * Physical recovery for a planned CREATE whose table already exists (the model's
     * {@code sys_*} rows were removed while the physical table survived): the CREATE above
     * degrades to already-applied, and the pre-existing table is adopted by adding whatever
     * declared columns / indexes it physically lacks — instead of silently keeping the drift.
     */
    private void renderAdoptedTableChanges(DdlDialect dialect, ModelOps op, PhysicalSchema facts,
                                           List<RenderedDdl> out) {
        String table = effectiveTableName(op.model());
        if (facts == null || !facts.tableExists(table)) {
            return;
        }
        for (SysField field : op.createFields()) {
            if (!facts.columnExists(table, effectiveColumnName(field))) {
                renderFieldChange(dialect,
                        SysDdlContextBuilder.forAlter(op.model(), List.of(field), List.of(), List.of(), List.of()),
                        RenderedDdl.Kind.ALTER_TABLE,
                        "ADD COLUMN " + columnLabel(op.model(), field) + " " + RECOVERY_TAG
                                + " adopted pre-existing table", out);
            }
        }
        Map<String, String> fieldToColumn = new HashMap<>();
        addAllFieldMappings(fieldToColumn, op.createFields());
        for (SysModelIndex index : op.createIndexes()) {
            if (!facts.indexExists(table, index.getIndexName())) {
                renderIndexChange(dialect,
                        SysDdlContextBuilder.forIndexChanges(op.model(), fieldToColumn,
                                List.of(index), List.of(), List.of()),
                        "ADD INDEX " + index.getIndexName() + " " + RECOVERY_TAG
                                + " adopted pre-existing table", out);
            }
        }
    }

    /**
     * Per-change ALTER rendering: every added / modified / declared-renamed column and
     * every added / rebuilt index becomes its own {@link RenderedDdl} (see the class
     * javadoc on why batching would trade correctness for round-trips). Deleted
     * columns / indexes never render here — they are warn-only hints
     * ({@link #renderDropColumn} / {@link #renderDropIndex}).
     */
    private void renderAlter(DdlDialect dialect, ModelOps op,
                             Map<String, String> modelFieldToColumn, PhysicalSchema facts, List<RenderedDdl> out) {
        SysModel model = op.model();
        String table = effectiveTableName(model);
        for (SysField field : op.fields().added()) {
            renderFieldChange(dialect,
                    SysDdlContextBuilder.forAlter(model, List.of(field), List.of(), List.of(), List.of()),
                    RenderedDdl.Kind.ALTER_TABLE,
                    "ADD COLUMN " + columnLabel(model, field), out);
        }
        for (SysField field : op.fields().updated()) {
            // Physical recovery: a MODIFY against a hand-dropped column would fail the boot —
            // recreate the column first. The original MODIFY still renders below: a no-op
            // re-assert on the recreated column, and the real change when the recovery ADD
            // degraded because the facts were stale.
            if (columnMissingPhysically(facts, table, field)) {
                renderFieldChange(dialect,
                        SysDdlContextBuilder.forAlter(model, List.of(field), List.of(), List.of(), List.of()),
                        RenderedDdl.Kind.ALTER_TABLE,
                        "ADD COLUMN " + columnLabel(model, field) + " " + RECOVERY_TAG
                                + " column missing behind planned MODIFY", out);
            }
            // Narrowing policy: a MODIFY re-states the full column definition, so executing it
            // against a physically wider (or type-incomparable) column could truncate data —
            // even when the triggering delta was only a comment. Widen freely, never narrow
            // silently; without facts the pre-introspection behavior stands.
            PhysicalSchema.PhysicalColumn observed =
                    facts == null ? null : facts.column(table, effectiveColumnName(field));
            PhysicalTypeCompat.Verdict verdict =
                    observed == null ? null : PhysicalTypeCompat.compare(field, observed);
            if (verdict == PhysicalTypeCompat.Verdict.NARROW
                    || verdict == PhysicalTypeCompat.Verdict.INCOMPARABLE) {
                renderFieldChange(dialect,
                        SysDdlContextBuilder.forAlter(model, List.of(), List.of(field), List.of(), List.of()),
                        RenderedDdl.Kind.ALTER_NARROWING,
                        "MODIFY COLUMN " + columnLabel(model, field)
                                + " [" + verdict.name().toLowerCase(Locale.ROOT) + ": "
                                + PhysicalTypeCompat.describe(field, observed) + "]", out);
                continue;
            }
            renderFieldChange(dialect,
                    SysDdlContextBuilder.forAlter(model, List.of(), List.of(field), List.of(), List.of()),
                    RenderedDdl.Kind.ALTER_TABLE,
                    "MODIFY COLUMN " + columnLabel(model, field), out);
        }
        for (DdlPolicy.FieldRename rename : op.fields().renamed()) {
            // Physical recovery: a declared rename whose old AND new columns are both physically
            // gone cannot be expressed as CHANGE COLUMN — create the new-shape column; the
            // original CHANGE below then degrades via the old-column-gone classification.
            if (facts != null && facts.tableExists(table)
                    && !facts.columnExists(table, rename.oldColumnName())
                    && !facts.columnExists(table, effectiveColumnName(rename.field()))) {
                renderFieldChange(dialect,
                        SysDdlContextBuilder.forAlter(model, List.of(rename.field()), List.of(), List.of(), List.of()),
                        RenderedDdl.Kind.ALTER_TABLE,
                        "ADD COLUMN " + columnLabel(model, rename.field()) + " " + RECOVERY_TAG
                                + " both rename sides missing", out);
            }
            renderFieldChange(dialect,
                    SysDdlContextBuilder.forAlter(model, List.of(), List.of(), List.of(rename), List.of()),
                    RenderedDdl.Kind.DECLARED_COLUMN_RENAME,
                    "CHANGE COLUMN " + rename.oldColumnName() + " -> "
                            + columnLabel(model, rename.field()), out);
        }
        renderIndexChanges(dialect, op, modelFieldToColumn, out);
    }

    /** True only on a positive fact: the table was introspected and lacks this field's column. */
    private static boolean columnMissingPhysically(PhysicalSchema facts, String table, SysField field) {
        return facts != null && facts.tableExists(table)
                && !facts.columnExists(table, effectiveColumnName(field));
    }

    private void renderFieldChange(DdlDialect dialect, ModelDdlCtx ctx,
                                   RenderedDdl.Kind kind, String label, List<RenderedDdl> out) {
        if (!ctx.isHasAlterTableChanges()) {
            return;   // e.g. the single field is not stored
        }
        String sql = dialect.alterTableDDL(ctx).toString().trim();
        if (!sql.isEmpty()) {
            out.add(RenderedDdl.of(kind, label, sql));
        }
    }

    private static String columnLabel(SysModel model, SysField field) {
        return effectiveColumnName(field) + " ON " + effectiveTableName(model);
    }

    private static String effectiveColumnName(SysField field) {
        return SysDdlContextBuilder.resolveColumnName(field);
    }

    private void renderIndexChanges(DdlDialect dialect, ModelOps op,
                                    Map<String, String> modelFieldToColumn, List<RenderedDdl> out) {
        if (op.indexes().added().isEmpty() && op.indexes().updated().isEmpty()) {
            return;
        }
        // Resolve field→column for index column translation. Start from the
        // complete from-code field→column map for this model (covers pre-existing
        // untouched fields with custom columnName), then overlay with diff buckets
        // (which may have newer values for added/updated fields).
        Map<String, String> fieldToColumn = new HashMap<>(modelFieldToColumn);
        addAllFieldMappings(fieldToColumn, op.fields().added());
        addAllFieldMappings(fieldToColumn, op.fields().updated());

        for (SysModelIndex index : op.indexes().added()) {
            renderIndexChange(dialect,
                    SysDdlContextBuilder.forIndexChanges(op.model(), fieldToColumn,
                            List.of(index), List.of(), List.of()),
                    "ADD INDEX " + index.getIndexName(), out);
        }
        // A definition change rebuilds: DROP INDEX + ADD INDEX, two statements executed
        // and classified separately (a missing index on the DROP half degrades via
        // DdlErrorClassifier.isIndexDropAlreadyApplied and the ADD still runs).
        for (SysModelIndex index : op.indexes().updated()) {
            renderIndexChange(dialect,
                    SysDdlContextBuilder.forIndexChanges(op.model(), fieldToColumn,
                            List.of(), List.of(index), List.of()),
                    "REBUILD INDEX " + index.getIndexName(), out);
        }
    }

    private void renderIndexChange(DdlDialect dialect, ModelDdlCtx ctx, String label,
                                   List<RenderedDdl> out) {
        if (!ctx.isHasIndexChanges()) {
            return;
        }
        String sql = dialect.alterIndexDDL(ctx).toString().trim();
        if (!sql.isEmpty()) {
            out.add(RenderedDdl.of(RenderedDdl.Kind.ALTER_INDEX, label, sql));
        }
    }

    private static void addAllFieldMappings(Map<String, String> target, List<SysField> fields) {
        for (SysField f : fields) {
            if (f.getFieldName() != null && f.getColumnName() != null) {
                target.put(f.getFieldName(), f.getColumnName());
            }
        }
    }

    /**
     * Table renames, two flavours:
     * <ul>
     *   <li><b>Declared</b> ({@code kind == RENAME}, the {@code renamedFrom} attribute on the
     *       model): the intent and the data-preserving target are explicit, so the
     *       {@code RENAME TABLE old TO new} <b>auto-executes</b>.</li>
     *   <li><b>Undeclared</b> ({@code kind == MODIFY}, a bare {@code tableName}-attribute
     *       change): could equally be a silent data divorce, so it stays
     *       <b>warn-only</b> with copy-paste SQL — the same risk class as DROP.
     *       Without surfacing it the change would be fully silent: the catalog points
     *       at the new name while the physical table keeps the old one, and every
     *       runtime query on the model fails.</li>
     * </ul>
     * A declared model rename's fields / indexes were re-keyed by the
     * {@link DiffEngine} cascade, so they show no
     * churn here; the row-side {@code modelName} cascade is done by the writer.
     */
    private void renderTableRenames(SchemaDiff diff, List<RenderedDdl> out) {
        for (SchemaDiff.Modification<SysModel> mod : diff.models().modified()) {
            if (Boolean.TRUE.equals(mod.fromCode().getProjection())) {
                // A projection owns no table: a tableName change repoints it at a different
                // owner's table (row-only), it does not rename the physical table.
                continue;
            }
            String oldTable = effectiveTableName(mod.fromDb());
            String newTable = effectiveTableName(mod.fromCode());
            if (oldTable.equals(newTable)) {
                continue;
            }
            // ALTER TABLE ... RENAME TO ... is valid across MySQL and PostgreSQL
            // (a single portable form — MySQL also accepts the RENAME TABLE idiom).
            String sql = "ALTER TABLE " + oldTable + " RENAME TO " + newTable + ";";
            boolean declared = mod.kind() == SchemaDiff.Kind.RENAME;
            out.add(RenderedDdl.of(
                    declared ? RenderedDdl.Kind.DECLARED_TABLE_RENAME : RenderedDdl.Kind.UNDECLARED_TABLE_RENAME,
                    "model " + mod.fromCode().getModelName() + " tableName " + oldTable + " -> " + newTable
                            + (declared ? " (declared renamedFrom)" : ""),
                    sql));
        }
    }

    private static String effectiveTableName(SysModel model) {
        return SysDdlContextBuilder.resolveTableName(model);
    }

    private void renderDropTable(DdlDialect dialect, ModelOps op, List<RenderedDdl> out) {
        ModelDdlCtx ctx = SysDdlContextBuilder.forDrop(op.model());
        String hintSql = safeDropSql(dialect, ctx);
        out.add(RenderedDdl.of(RenderedDdl.Kind.DROP_TABLE,
                "model " + op.model().getModelName() + " removed (DROP TABLE)", hintSql));
    }

    private void renderDropIndex(DdlDialect dialect, ModelOps op, List<RenderedDdl> out) {
        if (op.indexes().deleted().isEmpty()) {
            return;
        }
        ModelDdlCtx ctx = SysDdlContextBuilder.forIndexChanges(
                op.model(), Map.of(),
                List.of(), List.of(), op.indexes().deleted());
        String hintSql = dialect.alterIndexDDL(ctx).toString().trim();
        out.add(RenderedDdl.of(RenderedDdl.Kind.DROP_INDEX,
                op.indexes().deleted().size() + " index(es) removed on model " + op.model().getModelName(),
                hintSql));
    }

    private void renderDropColumn(DdlDialect dialect, ModelOps op, List<RenderedDdl> out) {
        if (op.fields().deleted().isEmpty()) {
            return;
        }
        // Build a "drop-only" context and render via alterTableDDL; the
        // resulting SQL contains the DROP COLUMN block.
        ModelDdlCtx ctx = SysDdlContextBuilder.forAlter(
                op.model(), List.of(), List.of(), List.of(), op.fields().deleted());
        if (!ctx.isHasAlterTableChanges()) {
            return;
        }
        String hintSql = dialect.alterTableDDL(ctx).toString().trim();
        out.add(RenderedDdl.of(RenderedDdl.Kind.DROP_COLUMN,
                op.fields().deleted().size() + " column(s) removed on model " + op.model().getModelName(),
                hintSql));
    }

    private String safeDropSql(DdlDialect dialect, ModelDdlCtx ctx) {
        try {
            return dialect.dropTableDDL(ctx).toString().trim();
        } catch (RuntimeException e) {
            return "DROP TABLE " + ctx.getTableName() + ";  -- (template render failed: " + e.getMessage() + ")";
        }
    }

    // ---- execute + classify failures ----------------------------------

    /**
     * Execute one statement. Returns {@code true} when executed, {@code false}
     * when skipped as already applied; a genuine failure logs the statement and
     * rethrows (fail-fast, rows stay unwritten).
     */
    private boolean executeStatement(RenderedDdl.Kind kind, String label, String statement) {
        try {
            jdbcTemplate.execute(statement);
            log.info("DdlOrchestrator: {} OK", label);
            return true;
        } catch (BadSqlGrammarException e) {
            if (isAlreadyApplied(kind, e)) {
                log.warn("DdlOrchestrator: {} — statement skipped (already applied: {})", label,
                        DdlErrorClassifier.rootMessage(e));
                return false;
            }
            log.error("DdlOrchestrator: {} FAILED. Statement was:\n{}", label, statement);
            throw e;
        } catch (DataAccessException e) {
            log.error("DdlOrchestrator: {} FAILED. Statement was:\n{}", label, statement);
            throw e;
        }
    }

    /**
     * "Already applied" = the common idempotent-duplicate set, plus the narrow
     * source-already-gone state for the kinds that legitimately re-run against a
     * renamed / rebuilt schema: a {@code CHANGE COLUMN} whose old column is gone
     * ({@code DECLARED_COLUMN_RENAME}), a {@code RENAME TABLE} whose old table is
     * gone ({@code DECLARED_TABLE_RENAME}), and the DROP half of an index rebuild
     * whose index is gone ({@code ALTER_INDEX}). Scoping by kind keeps a genuine
     * unknown-column / missing-table error on an ordinary ALTER surfacing as a
     * hard failure.
     */
    private static boolean isAlreadyApplied(RenderedDdl.Kind kind, BadSqlGrammarException e) {
        if (DdlErrorClassifier.isIdempotentDuplicate(e)) {
            return true;
        }
        return switch (kind) {
            case DECLARED_COLUMN_RENAME -> DdlErrorClassifier.isColumnRenameAlreadyApplied(e);
            case DECLARED_TABLE_RENAME -> DdlErrorClassifier.isTableRenameAlreadyApplied(e);
            case ALTER_INDEX -> DdlErrorClassifier.isIndexDropAlreadyApplied(e);
            default -> false;
        };
    }

    // ---- dialect ------------------------------------------------------

    private DdlDialect resolveDialect() {
        DatabaseType type = DBUtil.parseDatabaseType(datasourceUrl);
        return DdlDialectFactory.create(type, metadataResolver);
    }
}
