package io.softa.app.metadata;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.enums.StorageType;
import io.softa.starter.metadata.ddl.DdlDialectFactory;
import io.softa.starter.metadata.ddl.ReferenceColumnResolver;
import io.softa.starter.metadata.ddl.SysDdlContextBuilder;
import io.softa.starter.metadata.ddl.context.ModelDdlCtx;
import io.softa.starter.metadata.ddl.dialect.DdlDialect;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;
import io.softa.starter.metadata.scanner.ClasspathScannerSupport;
import io.softa.starter.metadata.scanner.annotation.AnnotationParser;
import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;

import static org.junit.jupiter.api.Assertions.assertFalse;

/**
 * Regenerates {@code deploy/init_mysql/1.Metadata.ddl.sql} from the single
 * source of truth: the {@code @Model} / {@code @Field} / {@code @Index} annotations on
 * demo-app's classpath (the framework and every
 * starter on demo-app's classpath under {@code io.softa}), rendered through the softa MySQL DDL dialect —
 * byte-for-byte the SQL a fresh boot with {@code scanner-scope: ["*"]} would execute.
 *
 * <p>Exists because the baseline is a generated artifact that historically drifted
 * behind the entities (pre-{@code app_code} / {@code label_name} era columns), and the
 * migration scripts that once covered the gap have been retired. Hand-editing the
 * baseline is how it rots; regenerate it instead:
 *
 * <pre>
 * REGEN_BASELINE=true mvn test -pl apps/demo-app -Dtest=MetadataBaselineDdlGeneratorTest
 * </pre>
 *
 * <p>Gated behind the environment variable so a normal test run never rewrites a
 * deploy artifact as a side effect.
 *
 * <p>The output is <b>intentionally untracked</b>: fresh databases self-bootstrap
 * (catalog reconcile + the {@code ["*"]} scanner), so the repo carries no DDL
 * baseline to rot — this generator exists for DB-first workflows and DBA review.
 */
class MetadataBaselineDdlGeneratorTest {

    private static final Path TARGET = Path.of("../../deploy/init_mysql/1.Metadata.ddl.sql");

    @Test
    @EnabledIfEnvironmentVariable(named = "REGEN_BASELINE", matches = "true")
    void regenerate() throws Exception {
        ClasspathScannerSupport scanner = new ClasspathScannerSupport(List.of("io.softa"));
        Set<Class<?>> modelClasses = scanner.findModelClasses();
        Set<Class<?>> optionSetEnums = scanner.findOptionSetEnums();
        AnnotationScanResult parsed = new AnnotationParser().parse(modelClasses, optionSetEnums);
        // TO_ONE FK columns physically mirror the referenced id/code — stamp before rendering,
        // exactly as the boot scanner does.
        ReferenceColumnResolver.stampSysFields(parsed.fields());

        DdlDialect dialect = DdlDialectFactory.create(DatabaseType.MYSQL, BuiltinDdlMetadataResolver.INSTANCE);
        List<SysModel> models = parsed.models().stream()
                .filter(m -> m.getStorageType() == null || m.getStorageType() == StorageType.RDBMS)
                .sorted(Comparator.comparing(SysDdlContextBuilder::resolveTableName))
                .toList();

        StringBuilder out = new StringBuilder();
        out.append("""
                -- ============================================================================
                -- Softa framework metadata baseline DDL — GENERATED, do not hand-edit.
                --
                -- Source of truth: the @Model / @Field / @Index annotations on demo-app's
                -- classpath (io.softa.*), rendered through the softa MySQL
                -- DDL dialect — identical to what a fresh boot with scanner-scope ["*"]
                -- would create. Regenerate with:
                --
                --   REGEN_BASELINE=true mvn test -pl apps/demo-app -Dtest=MetadataBaselineDdlGenerator
                --
                -- Intentionally untracked: fresh databases self-bootstrap (catalog reconcile
                -- + the ["*"] scanner). This file exists for DB-first review workflows only.
                -- ============================================================================

                """);
        // Several models may share one physical table on purpose (a read-projection
        // declares tableName = the owning model's table, e.g. EmployeeAsOfDateReport →
        // emp_change_record). A fresh ["*"] boot CREATEs the table once for the owner and
        // the projection's CREATE degrades to adoption (add whatever columns it declares
        // that the table lacks) — mirror exactly that: one CREATE per table (owner = the
        // model whose table name is the natural snake_case of its model name), then
        // adoption ALTERs for any extra stored columns a sharing model declares.
        Map<String, List<SysModel>> byTable = new LinkedHashMap<>();
        for (SysModel model : models) {
            byTable.computeIfAbsent(SysDdlContextBuilder.resolveTableName(model), t -> new ArrayList<>()).add(model);
        }
        int rendered = 0;
        for (Map.Entry<String, List<SysModel>> entry : byTable.entrySet()) {
            List<SysModel> sharing = entry.getValue();
            // Owner preference: a derived (undeclared) tableName, then a declared one that
            // merely restates snake_case(modelName) — projections point at ANOTHER model's
            // natural table, so their declared name never matches their own model name.
            SysModel owner = sharing.stream()
                    .filter(m -> m.getTableName() == null || m.getTableName().isBlank())
                    .findFirst()
                    .orElseGet(() -> sharing.stream()
                            .filter(m -> entry.getKey().equals(
                                    io.softa.framework.base.utils.StringTools.toUnderscoreCase(m.getModelName())))
                            .findFirst().orElse(sharing.getFirst()));
            ModelDdlCtx ctx = SysDdlContextBuilder.forCreate(
                    owner, fieldsOf(parsed, owner), indexesOf(parsed, owner));
            if (ctx.getCreatedFields().isEmpty()) {
                continue;   // nothing stored (pure-relation/DTO model)
            }
            out.append("-- ").append(owner.getModelName()).append('\n');
            out.append(dialect.createTableDDL(ctx).toString().trim()).append("\n\n");
            rendered++;
            Set<String> ownedColumns = fieldsOf(parsed, owner).stream()
                    .map(SysDdlContextBuilder::resolveColumnName).collect(java.util.stream.Collectors.toSet());
            for (SysModel projection : sharing) {
                if (projection == owner) {
                    continue;
                }
                List<SysField> extras = fieldsOf(parsed, projection).stream()
                        .filter(SysDdlContextBuilder::isStored)
                        .filter(f -> !ownedColumns.contains(SysDdlContextBuilder.resolveColumnName(f)))
                        .toList();
                if (extras.isEmpty()) {
                    out.append("-- ").append(projection.getModelName())
                            .append(" shares table ").append(entry.getKey())
                            .append(" (projection; no extra stored columns)\n\n");
                    continue;
                }
                ModelDdlCtx alter = SysDdlContextBuilder.forAlter(
                        projection, extras, List.of(), List.of(), List.of());
                if (alter.isHasAlterTableChanges()) {
                    out.append("-- ").append(projection.getModelName())
                            .append(" shares table ").append(entry.getKey())
                            .append(" (projection; adopts extra columns)\n");
                    out.append(dialect.alterTableDDL(alter).toString().trim()).append("\n\n");
                    extras.forEach(f -> ownedColumns.add(SysDdlContextBuilder.resolveColumnName(f)));
                }
            }
        }

        assertFalse(rendered == 0, "no models rendered — classpath scan came up empty?");
        Files.createDirectories(TARGET.toAbsolutePath().normalize().getParent());
        Files.writeString(TARGET.toAbsolutePath().normalize(), out.toString());
        System.out.printf("baseline DDL regenerated: %d table(s) → %s%n",
                rendered, TARGET.toAbsolutePath().normalize());
    }

    private static List<SysField> fieldsOf(AnnotationScanResult parsed, SysModel model) {
        return parsed.fields().stream()
                .filter(f -> model.getModelName().equals(f.getModelName())).toList();
    }

    private static List<SysModelIndex> indexesOf(AnnotationScanResult parsed, SysModel model) {
        return parsed.modelIndexes().stream()
                .filter(i -> model.getModelName().equals(i.getModelName())).toList();
    }
}
