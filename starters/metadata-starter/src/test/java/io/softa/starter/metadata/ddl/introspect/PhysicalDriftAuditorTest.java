package io.softa.starter.metadata.ddl.introspect;

import java.sql.Types;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalTable;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pure-function coverage for {@link PhysicalDriftAuditor}: every report bucket, the
 * stored-fields-only expectation (dynamic / TO_MANY fields never expect a column), and the
 * primary-key index exclusion.
 */
class PhysicalDriftAuditorTest {

    // ---- fixtures --------------------------------------------------------

    private static SysModel model(String name, String tableName) {
        SysModel m = new SysModel();
        m.setModelName(name);
        m.setTableName(tableName);
        return m;
    }

    private static SysField field(String modelName, String fieldName, FieldType type) {
        SysField f = new SysField();
        f.setModelName(modelName);
        f.setFieldName(fieldName);
        f.setFieldType(type);
        return f;
    }

    private static SysField dynamicField(String modelName, String fieldName) {
        SysField f = field(modelName, fieldName, FieldType.STRING);
        f.setDynamic(true);
        return f;
    }

    private static SysModelIndex index(String modelName, String indexName) {
        SysModelIndex i = new SysModelIndex();
        i.setModelName(modelName);
        i.setIndexName(indexName);
        return i;
    }

    private static PhysicalColumn varcharColumn(String name, int size) {
        return new PhysicalColumn(name, Types.VARCHAR, size, null, Boolean.TRUE);
    }

    private static PhysicalColumn bigintColumn(String name) {
        return new PhysicalColumn(name, Types.BIGINT, null, null, Boolean.FALSE);
    }

    private static PhysicalSchema schema(PhysicalTable... tables) {
        Map<String, PhysicalTable> byName = new LinkedHashMap<>();
        for (PhysicalTable t : tables) {
            byName.put(t.name().toLowerCase(), t);
        }
        return new PhysicalSchema(byName);
    }

    private static PhysicalTable table(String name, Set<String> indexNames, PhysicalColumn... columns) {
        Map<String, PhysicalColumn> byName = new LinkedHashMap<>();
        for (PhysicalColumn c : columns) {
            byName.put(c.name().toLowerCase(), c);
        }
        return new PhysicalTable(name, byName, indexNames);
    }

    // ---- buckets ---------------------------------------------------------

    @Test
    void reportsEveryDriftBucket() {
        SysModel customer = model("Customer", "customer");
        SysModel order = model("Order", "biz_order");
        List<SysField> fields = List.of(
                field("Customer", "id", FieldType.LONG),
                field("Customer", "email", FieldType.STRING),        // physically missing
                field("Order", "id", FieldType.LONG));
        List<SysModelIndex> indexes = List.of(
                index("Customer", "uk_customer_email"));              // physically missing

        // customer exists with id + a hand-added extra column and a hand-made index;
        // biz_order was hand-dropped entirely.
        PhysicalSchema facts = schema(
                table("customer", Set.of("idx_hand_made", "primary_key_7"),
                        bigintColumn("id"), varcharColumn("legacy_note", 100)));

        PhysicalDriftReport report = PhysicalDriftAuditor.audit(
                List.of(customer, order), fields, indexes, facts);

        assertEquals(List.of("biz_order (model Order)"), report.missingTables());
        assertEquals(List.of("customer.email (Customer.email)"), report.missingColumns());
        assertEquals(List.of("customer.uk_customer_email"), report.missingIndexes());
        assertEquals(List.of("customer.legacy_note"), report.undeclaredColumns());
        // primary_key_7 is a PK backing index — excluded; the hand-made one is reported.
        assertEquals(List.of("customer.idx_hand_made"), report.undeclaredIndexes());
        assertEquals(5, report.total());
        assertFalse(report.isEmpty());
    }

    @Test
    void consistentSchemaReportsEmpty() {
        SysModel customer = model("Customer", "customer");
        List<SysField> fields = List.of(
                field("Customer", "id", FieldType.LONG),
                field("Customer", "email", FieldType.STRING));
        List<SysModelIndex> indexes = List.of(index("Customer", "uk_customer_email"));

        PhysicalSchema facts = schema(
                table("customer", Set.of("uk_customer_email", "primary"),
                        bigintColumn("id"), varcharColumn("email", 64)));

        PhysicalDriftReport report = PhysicalDriftAuditor.audit(
                List.of(customer), fields, indexes, facts);

        assertTrue(report.isEmpty());
        assertEquals(0, report.total());
    }

    @Test
    void nonStoredFieldsNeverExpectAColumn() {
        SysModel customer = model("Customer", "customer");
        List<SysField> fields = List.of(
                field("Customer", "id", FieldType.LONG),
                dynamicField("Customer", "summary"),                       // dynamic → not stored
                field("Customer", "lines", FieldType.ONE_TO_MANY));        // TO_MANY → not stored

        PhysicalSchema facts = schema(table("customer", Set.of(), bigintColumn("id")));

        PhysicalDriftReport report = PhysicalDriftAuditor.audit(
                List.of(customer), fields, List.of(), facts);

        assertTrue(report.missingColumns().isEmpty(),
                "dynamic / TO_MANY fields have no physical column to miss");
        assertTrue(report.isEmpty());
    }

    @Test
    void typeMismatchIsReportedWithVerdict() {
        // sys_field says 512 but the physical column is still VARCHAR(64): the pending widen
        // must be visible every boot — and the same bucket keeps a deferred narrowing alive
        // after its one-time WARN.
        SysModel customer = model("Customer", "customer");
        SysField payload = field("Customer", "payload", FieldType.STRING);
        payload.setLength(512);

        PhysicalSchema facts = schema(
                table("customer", Set.of(), varcharColumn("payload", 64)));

        PhysicalDriftReport report = PhysicalDriftAuditor.audit(
                List.of(customer), List.of(payload), List.of(), facts);

        assertEquals(1, report.typeMismatches().size());
        String entry = report.typeMismatches().get(0);
        assertTrue(entry.startsWith("customer.payload (Customer.payload):"), entry);
        assertTrue(entry.endsWith("[widen]"), entry);
    }

    @Test
    void derivedNamesResolveLikeTheDdlLayer() {
        // No explicit tableName / columnName: snake_case derivation must match what the DDL
        // renders, so the audit compares the same physical names the orchestrator creates.
        SysModel model = model("ExportTemplate", null);
        SysField field = field("ExportTemplate", "customFileTemplate", FieldType.BOOLEAN);

        PhysicalSchema facts = schema(table("export_template", Set.of(), bigintColumn("id")));

        PhysicalDriftReport report = PhysicalDriftAuditor.audit(
                List.of(model), List.of(field), List.of(), facts);

        assertEquals(List.of("export_template.custom_file_template (ExportTemplate.customFileTemplate)"),
                report.missingColumns());
    }
}
