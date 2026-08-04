package io.softa.starter.metadata.scanner;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.orm.meta.MetadataInitializer;
import io.softa.starter.metadata.checksum.CatalogFingerprint;
import io.softa.starter.metadata.config.MetadataProperties;
import io.softa.starter.metadata.ddl.DdlOrchestrator;
import io.softa.starter.metadata.ddl.ReferenceColumnResolver;
import io.softa.starter.metadata.ddl.introspect.PhysicalDriftAuditor;
import io.softa.starter.metadata.ddl.introspect.PhysicalDriftReport;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysOptionSet;
import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;
import io.softa.starter.metadata.scanner.checker.MetadataAnnotationChecker;
import io.softa.starter.metadata.scanner.diff.DiffEngine;
import io.softa.starter.metadata.scanner.diff.SchemaDiff;

/**
 * Boot-time pre-initializer that reconciles annotation-derived metadata with
 * the {@code sys_*} catalog, confined to the packages named by
 * {@code system.metadata.scanner-scope}.
 *
 * <p>The bean is always registered; it gates itself at runtime on the scope
 * ({@link ScannerScope}):
 * <ul>
 *   <li>empty / unset scope ⇒ no-op (the safe production default — the
 *       {@link MetadataAnnotationChecker}
 *       observes drift read-only instead);</li>
 *   <li>{@code ["*"]} ⇒ reconcile every package;</li>
 *   <li>a partial list ⇒ reconcile only in-scope models/option-sets. Rows
 *       outside the scope are never read into the diff, so they are never
 *       updated or deleted — this is what lets multiple developers share one
 *       dev database without clobbering each other's rows. (Caveats: scope is per-package not per-class, the baseline is the
 *       shared live {@code sys_*}, and physical-table collisions are not
 *       addressed.)</li>
 * </ul>
 *
 * <p><b>Aggregate roots are never auto-deleted, under any scope.</b> A {@code SysModel} /
 * {@code SysOptionSet} row with no Java class — plus its fields, indexes and option items,
 * which follow their root — is confined out of the diff rather than removed
 * ({@link ScannerScope#confineFromDb}); {@code ["*"]} additionally names them in a WARN with
 * copy-paste SQL. Attribute rows of a root that IS in code are reconciled normally, deletions
 * included: there the annotations own the attribute set.
 *
 * <p>Flow:
 * <ol>
 *   <li>Scan the classpath for all {@code @Model} / {@code @OptionSet} declarations.</li>
 *   <li>Keep those whose package matches the scope; additionally pull in any
 *       {@code @OptionSet} enum referenced by an in-scope model's field
 *       (transitive inclusion) so a shared enum in another package is never
 *       orphaned.</li>
 *   <li>Parse them into an {@link AnnotationScanResult} (the from-code state).</li>
 *   <li>Load the current {@code sys_*} rows via {@link SysJdbcLoader} and
 *       confine them to the in-scope key set ({@link ScannerScope#confineFromDb}).</li>
 *   <li>Diff via {@link DiffEngine}; apply DDL via {@link DdlOrchestrator}
 *       <b>first</b>, then the {@code sys_*} rows via {@link SysJdbcWriter} —
 *       a failed DDL leaves the rows unwritten so the next boot retries the
 *       same diff (re-applied DDL degrades to WARN on duplicates).</li>
 * </ol>
 *
 * <p>This scanner uses {@code JdbcTemplate} directly, never
 * {@code SysModelService} / {@code ModelManager}. That keeps the
 * {@code SysModel}-describes-{@code SysModel} self-reference acyclic—
 * annotations describe schema, scanner writes hard-coded SQL.
 */
@Slf4j
@Component
public class MetadataAnnotationScanner implements MetadataInitializer {

    private final MetadataReadPipeline pipeline;
    private final MetadataProperties properties;
    private final String appCode;
    private final SysJdbcWriter writer;
    private final DdlOrchestrator ddlOrchestrator;

    @Autowired
    public MetadataAnnotationScanner(
            MetadataReadPipeline pipeline,
            MetadataProperties properties,
            SystemConfig systemConfig,
            JdbcTemplate jdbcTemplate,
            @Value("${spring.datasource.url:}") String datasourceUrl) {
        this(pipeline, properties, systemConfig.getAppCode(),
                new SysJdbcWriter(jdbcTemplate, systemConfig.getAppCode(), datasourceUrl),
                new DdlOrchestrator(jdbcTemplate, BuiltinDdlMetadataResolver.INSTANCE, datasourceUrl));
    }

    /**
     * Test seam: inject pre-built write-side collaborators directly, so the
     * DDL-before-rows ordering of {@link #initialize()} can be asserted with
     * mocks.
     */
    MetadataAnnotationScanner(
            MetadataReadPipeline pipeline,
            MetadataProperties properties,
            String appCode,
            SysJdbcWriter writer,
            DdlOrchestrator ddlOrchestrator) {
        this.pipeline = pipeline;
        this.properties = properties;
        this.appCode = appCode;
        this.writer = writer;
        this.ddlOrchestrator = ddlOrchestrator;
    }

    @Override
    public void initialize() {
        // App identity is mandatory whenever metadata-starter is active —
        // every sys_* row must be attributable. Fail fast in every
        // mode (scanner AND checker/prod) before any catalog access.
        if (appCode == null || appCode.isBlank()) {
            throw new IllegalStateException(
                    """
                            system.app-code is not configured. metadata-starter requires a stable app identity\
                            : add e.g.
                              system:
                                app-code: my-app
                            to application.yml (format [a-z][a-z0-9-]{0,63}).""");
        }

        ScannerScope scope = ScannerScope.of(properties.scannerScope());
        if (scope.isEmpty()) {
            log.info("MetadataAnnotationScanner: scanner-scope empty; reconciler disabled (manage nothing)");
            return;
        }
        log.info("MetadataAnnotationScanner: scanner-scope active (matchAll={}), scanning classpath...",
                scope.matchesAll());

        Set<Class<?>> allModelClasses = pipeline.discoverModelClasses();
        Set<Class<?>> allOptionSetEnums = pipeline.discoverOptionSetEnums();

        List<Class<?>> inScopeModels = allModelClasses.stream()
                .filter(c -> scope.matches(c.getPackageName()))
                .toList();

        // Transitive inclusion: option-set enums whose package is in scope, plus
        // any referenced by an in-scope model's field — so a shared enum in an
        // out-of-scope package is never orphaned or deleted.
        // Parse in-scope models once; reused below for fromCode (avoids a second parse).
        AnnotationScanResult modelsResult = pipeline.parse(inScopeModels, List.of());
        Set<String> referencedCodes = modelsResult.fields().stream()
                .map(SysField::getOptionSetCode)
                .filter(Objects::nonNull)
                .collect(Collectors.toCollection(LinkedHashSet::new));
        List<Class<?>> inScopeEnums = scope.selectOptionSetEnums(allOptionSetEnums, referencedCodes);

        log.info("MetadataAnnotationScanner: {} in-scope @Model class(es), {} in-scope @OptionSet enum(s) "
                        + "(of {} / {} on classpath)",
                inScopeModels.size(), inScopeEnums.size(),
                allModelClasses.size(), allOptionSetEnums.size());

        if (inScopeModels.isEmpty() && inScopeEnums.isEmpty()) {
            log.info("MetadataAnnotationScanner: no in-scope @Model/@OptionSet for the configured scope; "
                    + "nothing to reconcile");
            return;
        }

        // Parse the in-scope option-sets and merge with the already-parsed models.
        AnnotationScanResult enumsResult = pipeline.parse(List.of(), inScopeEnums);
        AnnotationScanResult fromCode = new AnnotationScanResult(
                modelsResult.models(), modelsResult.fields(),
                enumsResult.optionSets(), enumsResult.optionItems(),
                modelsResult.modelIndexes());
        log.info("MetadataAnnotationScanner: from-code = {} model(s), {} field(s), {} option set(s), "
                        + "{} option item(s), {} index(es)",
                fromCode.models().size(), fromCode.fields().size(),
                fromCode.optionSets().size(), fromCode.optionItems().size(),
                fromCode.modelIndexes().size());

        Set<String> inScopeModelNames = fromCode.models().stream()
                .map(SysModel::getModelName).collect(Collectors.toSet());
        Set<String> inScopeOptionCodes = fromCode.optionSets().stream()
                .map(SysOptionSet::getOptionSetCode).collect(Collectors.toSet());

        AnnotationScanResult current = pipeline.loadCurrentState();

        // Mirror every TO_ONE FK's referenced column onto relatedFieldType + length/scale before the
        // diff, so the physical type is stored (diffable) and a referenced-column change re-stamps its
        // dependents → MODIFY. Resolve against the in-scope code fields AND the full platform catalog
        // (in-scope code last, so it wins): under a partial scanner-scope an in-scope FK may point at
        // an out-of-scope model — sourcing the referenced column from the DB avoids resetting
        // relatedFieldType to null and churning a spurious MODIFY.
        List<SysField> referenceUniverse = new ArrayList<>(current.fields());
        referenceUniverse.addAll(fromCode.fields());
        ReferenceColumnResolver.stampSysFields(fromCode.fields(), referenceUniverse);

        // Identity is immutable once materialized: rows stamped with a different
        // app_code mean this catalog belongs to another app (or the yml identity
        // was changed without migrating). Refuse to reconcile.
        AppIdentityGuard.assertMatches(current, appCode);

        AnnotationScanResult fromDb = scope.confineFromDb(
                current, inScopeModelNames, inScopeOptionCodes);
        log.info("MetadataAnnotationScanner: from-db (in-scope) = {} model(s), {} field(s), "
                        + "{} option set(s), {} option item(s)",
                fromDb.models().size(), fromDb.fields().size(),
                fromDb.optionSets().size(), fromDb.optionItems().size());
        warnCodelessRoots(scope, current, inScopeModelNames, inScopeOptionCodes);

        // One physical snapshot serves the whole-catalog drift audit AND the DDL recovery
        // planning below (null when introspection failed — recovery then degrades to plain
        // diff planning). The audit runs on every boot — an idempotent diff can still hide
        // hand-made physical drift, and this consolidated report surfaces all of it at once.
        PhysicalSchema facts = ddlOrchestrator.introspect(fromCode.models());
        log.info("MetadataAnnotationScanner: physical snapshot = {}",
                facts == null ? "unavailable (recovery planning disabled)"
                        : facts.tables().size() + " managed table(s)");
        PhysicalDriftReport physicalDrift = null;
        if (facts != null) {
            physicalDrift = PhysicalDriftAuditor.audit(
                    fromCode.models(), fromCode.fields(), fromCode.modelIndexes(), facts);
            PhysicalDriftAuditor.warn(physicalDrift, "MetadataAnnotationScanner");
        }

        SchemaDiff diff = pipeline.diff(fromCode, fromDb);
        if (diff.isEmpty()) {
            log.info("MetadataAnnotationScanner: no changes detected (idempotent boot)");
        } else {
            for (String summary : writer.changeSummary(diff)) {
                log.info("MetadataAnnotationScanner: diff {}", summary);
            }
            // DDL first (additive + ALTER COLUMN auto; DROP warn-only), sys_* rows
            // second: a DDL failure leaves the rows unwritten, so the next boot
            // recomputes the same diff and retries. The opposite order is
            // unrecoverable — once the rows are committed the next boot's diff is
            // empty and the failed DDL is never re-attempted. Re-running DDL that
            // already succeeded is absorbed by DdlErrorClassifier's
            // already-applied degradation (1050/1060/1061/42P07 → WARN).
            ddlOrchestrator.apply(diff, fromCode.models(), fromCode.fields(), fromCode.modelIndexes(), facts);
            writer.apply(diff);
            log.info("MetadataAnnotationScanner: applied {} row change(s) to sys_*", diff.totalCount());
        }

        // Stamp the configured identity onto platform rows that predate the
        // app_code column. Idempotent (fills NULLs only).
        int stamped = writer.backfillAppCode();
        if (stamped > 0) {
            log.info("MetadataAnnotationScanner: backfilled app_code='{}' on {} platform row(s)",
                    appCode, stamped);
        }

        // Resolve the surrogate FKs from their business codes now that all sys_* rows are
        // written and app_code is stamped. Runs unconditionally (even on an idempotent boot) so a row
        // left NULL by a prior partial boot self-heals. Idempotent — a deterministic re-derivation.
        int linked = writer.populateSurrogateFks();
        if (linked > 0) {
            log.info("MetadataAnnotationScanner: resolved surrogate FK(s) on {} sys_* row(s)", linked);
        }

        // Boot health snapshot for GET /metadata/status. The in-scope catalog now equals the
        // code by construction (either the diff was empty or it was just applied), so both
        // fingerprints are the code-side one.
        String fingerprint = CatalogFingerprint.of(fromCode.models(), fromCode.fields(),
                fromCode.modelIndexes(), fromCode.optionSets(), fromCode.optionItems());
        MetadataStatus.record(new MetadataStatus("scanner", fingerprint, fingerprint,
                true, diff.totalCount(), physicalDrift, LocalDateTime.now()));
    }

    /**
     * One consolidated WARN naming the catalog roots that have no {@code @Model} /
     * {@code @OptionSet} class. {@link ScannerScope#confineFromDb} never deletes them, so the
     * only path to cleanup is a human running the SQL — emit it ready to paste, children before
     * parents, scoped to this app's rows.
     *
     * <p>Only under a full {@code ["*"]} scope. A partial scope deliberately shares the catalog
     * with packages it does not manage, where a code-less row is somebody else's row rather than
     * an orphan — naming those every boot would be pure noise.
     */
    private void warnCodelessRoots(ScannerScope scope, AnnotationScanResult current,
                                   Set<String> inScopeModelNames,
                                   Set<String> inScopeOptionCodes) {
        if (!scope.matchesAll()) {
            return;
        }
        List<String> models = current.models().stream()
                .map(SysModel::getModelName)
                .filter(name -> !inScopeModelNames.contains(name))
                .distinct().sorted().toList();
        List<String> optionSets = current.optionSets().stream()
                .map(SysOptionSet::getOptionSetCode)
                .filter(code -> !inScopeOptionCodes.contains(code))
                .distinct().sorted().toList();
        if (models.isEmpty() && optionSets.isEmpty()) {
            return;
        }
        StringBuilder sql = new StringBuilder();
        for (String modelName : models) {
            sql.append("\n-- model ").append(modelName)
                    .append(deleteBy("sys_field", "model_name", modelName))
                    .append(deleteBy("sys_model_index", "model_name", modelName))
                    .append(deleteBy("sys_model", "model_name", modelName));
        }
        for (String optionSetCode : optionSets) {
            sql.append("\n-- option set ").append(optionSetCode)
                    .append(deleteBy("sys_option_item", "option_set_code", optionSetCode))
                    .append(deleteBy("sys_option_set", "option_set_code", optionSetCode));
        }
        log.warn("MetadataAnnotationScanner: {} model(s) and {} option set(s) in the catalog have no"
                        + " Java class and were LEFT UNTOUCHED — an aggregate root is never"
                        + " auto-deleted (it may be Studio- or seed-authored on purpose). Models: {};"
                        + " option sets: {}. If they really are orphans, run:{}",
                models.size(), optionSets.size(), models, optionSets, sql);
    }

    private String deleteBy(String table, String column, String value) {
        return "\nDELETE FROM " + table + " WHERE " + column + " = '" + value
                + "' AND app_code = '" + appCode + "';";
    }
}
