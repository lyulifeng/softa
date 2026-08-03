package io.softa.starter.metadata.checksum;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link CatalogFingerprint} must be order-insensitive (same catalog, any list order → same
 * fingerprint) and change-sensitive (any schema-relevant attribute delta → different
 * fingerprint) — the two properties {@code /metadata/status}'s in-sync answer stands on.
 */
class CatalogFingerprintTest {

    private static SysModel model(String name) {
        SysModel m = new SysModel();
        m.setModelName(name);
        m.setTableName(name.toLowerCase());
        return m;
    }

    private static SysField field(String modelName, String fieldName, Integer length) {
        SysField f = new SysField();
        f.setModelName(modelName);
        f.setFieldName(fieldName);
        f.setFieldType(FieldType.STRING);
        f.setLength(length);
        return f;
    }

    @Test
    void sameCatalogInAnyOrderFingerprintsEqual() {
        SysModel a = model("Alpha");
        SysModel b = model("Beta");
        SysField a1 = field("Alpha", "code", 64);
        SysField b1 = field("Beta", "name", 100);

        String forward = CatalogFingerprint.of(List.of(a, b), List.of(a1, b1),
                List.of(), List.of(), List.of());
        String shuffled = CatalogFingerprint.of(List.of(b, a), List.of(b1, a1),
                List.of(), List.of(), List.of());

        assertEquals(forward, shuffled);
    }

    @Test
    void attributeChangeChangesTheFingerprint() {
        SysModel a = model("Alpha");

        String at64 = CatalogFingerprint.of(List.of(a), List.of(field("Alpha", "code", 64)),
                List.of(), List.of(), List.of());
        String at512 = CatalogFingerprint.of(List.of(a), List.of(field("Alpha", "code", 512)),
                List.of(), List.of(), List.of());

        assertNotEquals(at64, at512, "a length change is schema-relevant and must be visible");
    }

    @Test
    void addedAggregateChangesTheFingerprint() {
        SysModel a = model("Alpha");
        String one = CatalogFingerprint.of(List.of(a), List.of(), List.of(), List.of(), List.of());
        String two = CatalogFingerprint.of(List.of(a, model("Beta")), List.of(),
                List.of(), List.of(), List.of());

        assertNotEquals(one, two);
    }
}
