package io.softa.framework.orm.meta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.jdbc.JdbcService;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Write-side field selection for {@link ModelManager}: {@code getModelUpdatableFields} is the set
 * every write path intersects the payload with, and the UPDATE SQL builder turns whatever survives
 * into a `SET column = ?` clause. A dynamic field owns no column, so it must not survive — while
 * XToMany fields, dynamic by definition but written through their own cascade processors, must.
 * Builds a real frozen snapshot through {@code init()} with a mocked {@link JdbcService}, because
 * the method reads snapshot internals directly.
 */
class ModelManagerUpdatableFieldsTest {

    private static Object previousSnapshot;

    @BeforeAll
    static void initSnapshot() throws Exception {
        if (SystemConfig.env == null) {
            SystemConfig.env = new SystemConfig();
        }
        previousSnapshot = snapshotField().get(null);

        JdbcService<?> jdbcService = Mockito.mock(JdbcService.class);
        Mockito.when(jdbcService.selectMetaEntityList("SysModel", MetaModel.class, null))
                .thenReturn(models());
        Mockito.when(jdbcService.selectMetaEntityList("SysField", MetaField.class, null))
                .thenReturn(fields());

        ModelManager modelManager = new ModelManager();
        Field jdbc = ModelManager.class.getDeclaredField("jdbcService");
        jdbc.setAccessible(true);
        jdbc.set(modelManager, jdbcService);
        modelManager.init();
    }

    @AfterAll
    static void restoreSnapshot() throws Exception {
        snapshotField().set(null, previousSnapshot);
    }

    private static Field snapshotField() throws Exception {
        Field field = ModelManager.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return field;
    }

    // ---- fixture ---------------------------------------------------------

    private static List<MetaModel> models() {
        // ArrayList, not List.of: ListUtils.allNotNull probes contains(null),
        // which immutable collections reject with NPE.
        return new ArrayList<>(List.of(
                model("UpdDoc", "upd_doc"),
                model("UpdLine", "upd_line")));
    }

    private static List<MetaField> fields() {
        return new ArrayList<>(List.of(
                field("UpdDoc", "id", "id", FieldType.LONG),
                field("UpdDoc", "name", "name", FieldType.STRING),
                field("UpdDoc", "amount", "amount", FieldType.BIG_DECIMAL),
                // Reported on read, stored nowhere — the shape of UserAccount.locked.
                dynamicField("UpdDoc", "summary", "summary", FieldType.STRING),
                oneToMany("UpdDoc", "lines", "UpdLine", "docId"),
                field("UpdLine", "id", "id", FieldType.LONG),
                manyToOne("UpdLine", "docId", "doc_id", "UpdDoc")));
    }

    private static MetaModel model(String modelName, String tableName) {
        MetaModel metaModel = new MetaModel();
        metaModel.setModelName(modelName);
        metaModel.setLabel(modelName);
        metaModel.setTableName(tableName);
        return metaModel;
    }

    private static MetaField field(String modelName, String fieldName, String columnName, FieldType type) {
        MetaField metaField = new MetaField();
        metaField.setModelName(modelName);
        metaField.setFieldName(fieldName);
        metaField.setColumnName(columnName);
        metaField.setLabel(fieldName);
        metaField.setFieldType(type);
        return metaField;
    }

    private static MetaField dynamicField(String modelName, String fieldName, String columnName, FieldType type) {
        MetaField metaField = field(modelName, fieldName, columnName, type);
        metaField.setDynamic(true);
        return metaField;
    }

    private static MetaField manyToOne(String modelName, String fieldName, String columnName, String relatedModel) {
        MetaField metaField = field(modelName, fieldName, columnName, FieldType.MANY_TO_ONE);
        metaField.setRelatedModel(relatedModel);
        return metaField;
    }

    private static MetaField oneToMany(String modelName, String fieldName, String relatedModel, String relatedField) {
        MetaField metaField = field(modelName, fieldName, fieldName, FieldType.ONE_TO_MANY);
        metaField.setRelatedModel(relatedModel);
        metaField.setRelatedField(relatedField);
        return metaField;
    }

    // ---- assertions ------------------------------------------------------

    @Test
    void getModelUpdatableFields_excludesDynamicFieldAndKeepsStoredSiblings() {
        Set<String> updatable = ModelManager.getModelUpdatableFields("UpdDoc");
        assertFalse(updatable.contains("summary"),
                "a dynamic field owns no column: keeping it renders UPDATE upd_doc SET summary = ?");
        assertTrue(updatable.contains("name"), "stored siblings stay updatable");
        assertTrue(updatable.contains("amount"), "stored siblings stay updatable");
    }

    @Test
    void getModelUpdatableFields_keepsXToManyFields() {
        // XToMany fields are dynamic as well, but they are written on the related model by the
        // cascade processors; dropping them here would silently kill OneToMany/ManyToMany writes.
        assertTrue(ModelManager.getModelUpdatableFields("UpdDoc").contains("lines"));
    }

    @Test
    void getModelUpdatableFields_agreesWithTheCreatePathOnStoredness() {
        // The CREATE path keeps only isStored() fields in its INSERT column list; the two sides
        // must not drift, so nothing the UPDATE side accepts may be a non-XToMany dynamic field.
        ModelManager.getModelUpdatableFields("UpdDoc").stream()
                .filter(field -> !FieldType.TO_MANY_TYPES.contains(
                        ModelManager.getModelField("UpdDoc", field).getFieldType()))
                .forEach(field -> assertTrue(ModelManager.isStored("UpdDoc", field),
                        "updatable non-XToMany field is not stored: " + field));
    }
}
