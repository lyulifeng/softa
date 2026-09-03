package io.softa.starter.studio.release.desired;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.softa.framework.base.utils.StringTools;
import io.softa.framework.orm.enums.StorageType;
import io.softa.starter.metadata.entity.SysModelIndex;
import io.softa.starter.metadata.scanner.annotation.SearchIndexSynthesizer;
import io.softa.starter.studio.release.ddl.context.DdlContextBuilder;

/**
 * Applies the same search-index derivation the annotation lane applies, to the studio lane's
 * {@code design_*} attribute maps.
 *
 * <p><b>Both lanes have to derive, or the deploy fights the scanner.</b> A publish converges an
 * env's runtime to exactly its design state, so an index row that exists in
 * {@code sys_model_index} but not in the design side is "only in runtime" and gets DELETEd — then
 * the next boot's scanner derives it again, and the aggregate diff never reports "no change"
 * again. Deriving on both sides keeps the two catalogs byte-identical, which is also what keeps
 * the cross-lane checksums equal.
 *
 * <p>The rule itself lives in {@link SearchIndexSynthesizer}; this class only translates row maps
 * into its input. Reimplementing the {@code searchName} resolution here instead would put a
 * second copy of it one module away from the first, and the two would drift silently — a deploy
 * quietly dropping indexes is exactly the kind of divergence nobody notices until a search gets
 * slow.
 */
final class DesignSearchIndexSpecs {

    private DesignSearchIndexSpecs() {
    }

    /** The derived index rows, as attribute maps shaped like the ones {@code DesignEnvSource} loads. */
    static List<Map<String, Object>> derive(List<Map<String, Object>> models,
                                            List<Map<String, Object>> fields,
                                            List<Map<String, Object>> declaredIndexes) {
        List<SearchIndexSynthesizer.ModelSpec> specs = toSpecs(models, fields);
        List<SearchIndexSynthesizer.DeclaredIndex> declared = new ArrayList<>(declaredIndexes.size());
        for (Map<String, Object> row : declaredIndexes) {
            String method = DdlContextBuilder.asString(row.get("method"));
            declared.add(new SearchIndexSynthesizer.DeclaredIndex(
                    DdlContextBuilder.asString(row.get("modelName")),
                    DdlContextBuilder.asString(row.get("indexName")),
                    DdlContextBuilder.asStringList(row.get("indexFields")),
                    method != null && !method.isBlank()));
        }
        List<Map<String, Object>> rows = new ArrayList<>();
        for (SysModelIndex idx : SearchIndexSynthesizer.derive(specs, declared)) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("modelName", idx.getModelName());
            row.put("indexName", idx.getIndexName());
            row.put("indexFields", idx.getIndexFields());
            row.put("uniqueIndex", idx.getUniqueIndex());
            // The stored item code ("Search"), NOT the enum: these rows sit next to rows loaded
            // via modelService.searchList, whose OPTION values are the persisted @JsonValue codes,
            // and CanonicalMetadataSerializer encodes an enum ("e:SEARCH") differently from a
            // string ("s:Search") — an enum here would make every derived index checksum as
            // permanently different from its runtime twin.
            row.put("method", idx.getMethod().getType());
            rows.add(row);
        }
        return rows;
    }

    private static List<SearchIndexSynthesizer.ModelSpec> toSpecs(List<Map<String, Object>> models,
                                                                  List<Map<String, Object>> fields) {
        Map<String, List<SearchIndexSynthesizer.FieldSpec>> byModel = new LinkedHashMap<>();
        for (Map<String, Object> field : fields) {
            String modelName = DdlContextBuilder.asString(field.get("modelName"));
            String fieldName = DdlContextBuilder.asString(field.get("fieldName"));
            String columnName = DdlContextBuilder.asString(field.get("columnName"));
            byModel.computeIfAbsent(modelName, k -> new ArrayList<>())
                    .add(new SearchIndexSynthesizer.FieldSpec(
                            fieldName,
                            // Studio-authored rows may leave the column derived — resolve it the
                            // way the DDL layer does, so the derived index name never goes blank.
                            columnName == null || columnName.isBlank()
                                    ? StringTools.toUnderscoreCase(fieldName) : columnName,
                            DdlContextBuilder.asFieldType(field.get("fieldType")),
                            DdlContextBuilder.asBoolean(field.get("dynamic"))));
        }
        List<SearchIndexSynthesizer.ModelSpec> specs = new ArrayList<>(models.size());
        for (Map<String, Object> model : models) {
            String modelName = DdlContextBuilder.asString(model.get("modelName"));
            String tableName = DdlContextBuilder.asString(model.get("tableName"));
            specs.add(new SearchIndexSynthesizer.ModelSpec(
                    modelName,
                    tableName == null || tableName.isBlank()
                            ? StringTools.toUnderscoreCase(modelName) : tableName,
                    DdlContextBuilder.asStringList(model.get("searchName")),
                    DdlContextBuilder.asBoolean(model.get("projection")),
                    isRdbms(model.get("storageType")),
                    byModel.getOrDefault(modelName, List.of())));
        }
        return specs;
    }

    /** Null storageType means "not overridden", which is RDBMS — the DDL planner reads it the same way. */
    private static boolean isRdbms(Object value) {
        if (value == null) {
            return true;
        }
        if (value instanceof StorageType storageType) {
            return storageType == StorageType.RDBMS;
        }
        String str = DdlContextBuilder.asString(value);
        return str == null || str.isBlank()
                || StorageType.RDBMS.name().equalsIgnoreCase(str)
                || StorageType.RDBMS.getType().equalsIgnoreCase(str);
    }
}
