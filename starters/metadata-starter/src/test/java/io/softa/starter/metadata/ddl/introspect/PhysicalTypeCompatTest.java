package io.softa.starter.metadata.ddl.introspect;

import java.sql.Types;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.ddl.introspect.PhysicalTypeCompat.Verdict;
import io.softa.starter.metadata.entity.SysField;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * The verdict matrix behind the MODIFY policy: widen freely, never narrow silently,
 * incomparable degrades to report-only. Comparison is by {@code java.sql.Types}
 * equivalence class — never by engine type-name strings.
 */
class PhysicalTypeCompatTest {

    private static SysField declared(FieldType type, Integer length, Integer scale) {
        SysField f = new SysField();
        f.setModelName("M");
        f.setFieldName("f");
        f.setFieldType(type);
        f.setLength(length);
        f.setScale(scale);
        return f;
    }

    private static PhysicalColumn observed(int jdbcType, Integer size, Integer scale) {
        return new PhysicalColumn("f", jdbcType, size, scale, Boolean.TRUE);
    }

    private static Verdict verdict(SysField declared, PhysicalColumn observed) {
        return PhysicalTypeCompat.compare(declared, observed);
    }

    // ---- STRING widths ----------------------------------------------------

    @Test
    void stringWidths() {
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.STRING, 64, null),
                observed(Types.VARCHAR, 64, null)));
        assertEquals(Verdict.WIDEN, verdict(declared(FieldType.STRING, 512, null),
                observed(Types.VARCHAR, 64, null)));
        assertEquals(Verdict.NARROW, verdict(declared(FieldType.STRING, 64, null),
                observed(Types.VARCHAR, 512, null)));
        // Physical TEXT/CLOB family is unbounded: any bounded declaration is a narrowing.
        // (The OutboxEntry.payload incident: declared 512, physical TEXT.)
        assertEquals(Verdict.NARROW, verdict(declared(FieldType.STRING, 512, null),
                observed(Types.LONGVARCHAR, 16_777_215, null)));
        // Unknown widths never cry wolf.
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.STRING, null, null),
                observed(Types.VARCHAR, 64, null)));
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.STRING, 512, null),
                observed(Types.VARCHAR, null, null)));
    }

    @Test
    void textIsUnbounded() {
        // Declared TEXT matches any TEXT/CLOB-family column regardless of size.
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.TEXT, null, null),
                observed(Types.LONGVARCHAR, 16_777_215, null)));
        // A declared length on TEXT is only an app-level guard, not a width claim.
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.TEXT, 32767, null),
                observed(Types.LONGVARCHAR, 65_535, null)));
        // Migrating a legacy VARCHAR(n) column to TEXT is a widening MODIFY.
        assertEquals(Verdict.WIDEN, verdict(declared(FieldType.TEXT, null, null),
                observed(Types.VARCHAR, 32767, null)));
    }

    @Test
    void jsonIsUnboundedOnTheDeclaredSide() {
        // JSON renders TEXT-class: physically bounded VARCHAR means the pending MODIFY widens.
        assertEquals(Verdict.WIDEN, verdict(declared(FieldType.JSON, null, null),
                observed(Types.VARCHAR, 512, null)));
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.JSON, null, null),
                observed(Types.LONGVARCHAR, 16_777_215, null)));
    }

    // ---- numeric lattice ----------------------------------------------------

    @Test
    void numericLattice() {
        assertEquals(Verdict.WIDEN, verdict(declared(FieldType.LONG, null, null),
                observed(Types.INTEGER, 10, null)));
        assertEquals(Verdict.NARROW, verdict(declared(FieldType.INTEGER, null, null),
                observed(Types.BIGINT, 19, null)));
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.LONG, null, null),
                observed(Types.BIGINT, 19, null)));
    }

    @Test
    void booleanAcceptsMySqlTinyint() {
        // MySQL renders BOOLEAN as TINYINT and reports it back as an integer type.
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.BOOLEAN, null, null),
                observed(Types.TINYINT, 3, null)));
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.BOOLEAN, null, null),
                observed(Types.BOOLEAN, null, null)));
    }

    // ---- BIG_DECIMAL precision / scale --------------------------------------

    @Test
    void decimalPrecisionAndScale() {
        assertEquals(Verdict.EQUAL, verdict(declared(FieldType.BIG_DECIMAL, 10, 2),
                observed(Types.DECIMAL, 10, 2)));
        assertEquals(Verdict.WIDEN, verdict(declared(FieldType.BIG_DECIMAL, 32, 8),
                observed(Types.DECIMAL, 10, 2)));
        // A wider precision with a narrower scale still truncates decimals → NARROW.
        assertEquals(Verdict.NARROW, verdict(declared(FieldType.BIG_DECIMAL, 32, 1),
                observed(Types.DECIMAL, 10, 2)));
    }

    // ---- incomparable --------------------------------------------------------

    @Test
    void differentFamiliesAreIncomparable() {
        assertEquals(Verdict.INCOMPARABLE, verdict(declared(FieldType.BOOLEAN, null, null),
                observed(Types.VARCHAR, 64, null)));
        assertEquals(Verdict.INCOMPARABLE, verdict(declared(FieldType.STRING, 64, null),
                observed(Types.BIGINT, 19, null)));
        // Unmapped physical type (e.g. a vendor geometry type) — report, never act.
        assertEquals(Verdict.INCOMPARABLE, verdict(declared(FieldType.STRING, 64, null),
                observed(Types.OTHER, null, null)));
    }

    // ---- TO_ONE FK mirrors ----------------------------------------------------

    @Test
    void foreignKeyComparesThroughItsResolvedMirror() {
        // A MANY_TO_ONE FK's physical shape is the stamped relatedFieldType mirror, not the
        // relation type itself: a code-as-id FK mirroring STRING(3) vs physical VARCHAR(3).
        SysField fk = declared(FieldType.MANY_TO_ONE, 3, null);
        fk.setRelatedFieldType(FieldType.STRING);
        assertEquals(Verdict.EQUAL, verdict(fk, observed(Types.VARCHAR, 3, null)));
        assertEquals(Verdict.WIDEN, verdict(fk, observed(Types.VARCHAR, 2, null)));
    }
}
