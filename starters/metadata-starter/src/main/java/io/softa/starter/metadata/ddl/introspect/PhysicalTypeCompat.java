package io.softa.starter.metadata.ddl.introspect;

import java.sql.Types;
import java.util.List;
import java.util.Set;

import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.ddl.SysDdlContextBuilder;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.entity.SysField;

/**
 * Compares a declared field's physical target shape against an observed column — the verdict
 * behind the MODIFY auto-execute policy (widen freely, never narrow silently) and the drift
 * audit's type dimension.
 *
 * <p>Comparison is by {@code java.sql.Types} <b>equivalence class</b> on both sides
 * ({@link JdbcTypeReverse#primitiveClass}; the declared side enters through
 * {@link FieldType#getSqlType()}, TO_ONE FKs through their resolved {@code relatedFieldType}
 * mirror) — never by parsing engine type-name strings. Known imprecision degrades toward
 * {@link Verdict#INCOMPARABLE} (report, don't act), with two allowances where the declared
 * side's JDBC binding is not what the dialects actually render: a declared BOOLEAN accepts an
 * integer-class column (MySQL renders BOOLEAN as TINYINT and its driver reports it back as an
 * integer type), and a declared DOUBLE compares as BIG_DECIMAL — this framework's DOUBLE is
 * the measurement-sized member of the <i>exact</i> decimal cluster, not an IEEE float
 * ({@code BuiltinDdlMetadataResolver}: DOUBLE 24,2 / BIG_DECIMAL 32,8), so both dialects
 * render it {@code DECIMAL} / {@code NUMERIC} while {@code FieldType.DOUBLE.getSqlType()}
 * still reports {@code Types.DOUBLE} for the Java {@code Double} carrier.
 *
 * <p>Width rules: within the STRING class the declared width ({@code sys_field.length},
 * materialized to the real column width; JSON/DTO/TEXT are unbounded — they render TEXT-class)
 * compares against the observed width (LONGVARCHAR/CLOB-class observed types are unbounded);
 * an unknown side compares as {@link Verdict#EQUAL} — missing driver data must not cry wolf.
 * BIG_DECIMAL — and DOUBLE, through the allowance above — compares precision and scale
 * (any shrinking dimension ⇒ {@link Verdict#NARROW}).
 * Across numeric classes the INTEGER ⊂ LONG ⊂ BIG_DECIMAL lattice decides widen vs narrow.
 */
public final class PhysicalTypeCompat {

    private PhysicalTypeCompat() {
    }

    public enum Verdict {
        /** Same physical shape (or not enough information to say otherwise). */
        EQUAL,
        /** The declared shape is wider — the pending MODIFY grows the column, data-safe. */
        WIDEN,
        /** The declared shape is narrower — a MODIFY could truncate data; never auto-execute. */
        NARROW,
        /** Different type families — intent is unclear; report and leave to a human. */
        INCOMPARABLE
    }

    private static final int UNBOUNDED = Integer.MAX_VALUE;
    private static final int UNKNOWN = -1;

    /** Numeric widening lattice: a column may grow along it, never shrink. */
    private static final List<FieldType> NUMERIC_LATTICE =
            List.of(FieldType.INTEGER, FieldType.LONG, FieldType.BIG_DECIMAL);

    /** Observed JDBC types whose width is effectively unbounded (TEXT/CLOB family). */
    private static final Set<Integer> UNBOUNDED_JDBC_TYPES = Set.of(
            Types.LONGVARCHAR, Types.LONGNVARCHAR, Types.CLOB, Types.NCLOB);

    public static Verdict compare(SysField declared, PhysicalColumn observed) {
        FieldType declaredPhysical = SysDdlContextBuilder.resolvePhysicalFieldType(declared);
        if (declaredPhysical == null) {
            return Verdict.INCOMPARABLE;
        }
        FieldType declaredClass = JdbcTypeReverse.primitiveClass(declaredPhysical.getSqlType());
        FieldType observedClass = JdbcTypeReverse.primitiveClass(observed.jdbcType());
        if (declaredClass == null || observedClass == null) {
            return Verdict.INCOMPARABLE;
        }
        if (declaredClass == observedClass) {
            return sameClassVerdict(declaredClass, declaredPhysical, declared, observed);
        }
        if (declaredClass == FieldType.BOOLEAN && observedClass == FieldType.INTEGER) {
            return Verdict.EQUAL;   // MySQL BOOLEAN ⇄ TINYINT
        }
        if (declaredClass == FieldType.DOUBLE && observedClass == FieldType.BIG_DECIMAL) {
            // The declared class above comes from the Java carrier's JDBC binding, which for
            // DOUBLE alone disagrees with what the dialects render: DOUBLE is this framework's
            // measurement-sized EXACT decimal (24,2), so the column it created is DECIMAL /
            // NUMERIC and reads back as BIG_DECIMAL. Compare on the axis that column really
            // has — precision and scale — instead of degrading to INCOMPARABLE, which no DDL
            // can resolve: the convergence lane re-plans the same no-op MODIFY every boot (a
            // table rebuild, on MySQL). Deliberately one-way — a declared BIG_DECIMAL over a
            // physical float column IS drift, and a MODIFY to the declared shape fixes it
            // (holiday_calendar_detail.days: BigDecimal(24,2) over a legacy DOUBLE(24,2)).
            return decimalVerdict(declared, observed);
        }
        int declaredRank = NUMERIC_LATTICE.indexOf(declaredClass);
        int observedRank = NUMERIC_LATTICE.indexOf(observedClass);
        if (declaredRank >= 0 && observedRank >= 0) {
            return declaredRank > observedRank ? Verdict.WIDEN : Verdict.NARROW;
        }
        return Verdict.INCOMPARABLE;
    }

    /** Human-readable shape pair for labels and the drift report. */
    public static String describe(SysField declared, PhysicalColumn observed) {
        FieldType declaredPhysical = SysDdlContextBuilder.resolvePhysicalFieldType(declared);
        return "declared " + declaredPhysical
                + width(declaredWidth(declaredPhysical, declared))
                + " vs physical jdbcType " + observed.jdbcType()
                + width(observedWidth(observed));
    }

    private static Verdict sameClassVerdict(FieldType typeClass, FieldType declaredPhysical,
                                            SysField declared, PhysicalColumn observed) {
        if (typeClass == FieldType.STRING) {
            return widthVerdict(declaredWidth(declaredPhysical, declared), observedWidth(observed));
        }
        if (typeClass == FieldType.BIG_DECIMAL) {
            return decimalVerdict(declared, observed);
        }
        return Verdict.EQUAL;   // fixed-shape classes: width is not a degree of freedom
    }

    private static Verdict widthVerdict(int declared, int observed) {
        if (declared == UNKNOWN || observed == UNKNOWN || declared == observed) {
            return Verdict.EQUAL;
        }
        return declared > observed ? Verdict.WIDEN : Verdict.NARROW;
    }

    private static Verdict decimalVerdict(SysField declared, PhysicalColumn observed) {
        if (declared.getLength() == null || observed.size() == null
                || declared.getScale() == null || observed.scale() == null) {
            return Verdict.EQUAL;
        }
        int precisionDelta = declared.getLength().compareTo(observed.size());
        int scaleDelta = declared.getScale().compareTo(observed.scale());
        if (precisionDelta == 0 && scaleDelta == 0) {
            return Verdict.EQUAL;
        }
        // Any shrinking dimension can truncate; widening needs both dimensions non-shrinking.
        return precisionDelta >= 0 && scaleDelta >= 0 ? Verdict.WIDEN : Verdict.NARROW;
    }

    private static int declaredWidth(FieldType declaredPhysical, SysField declared) {
        // JSON / DTO / TEXT render as TEXT-class columns — width is not a real
        // bound (a declared TEXT length is only an app-level guard).
        if (declaredPhysical == FieldType.JSON || declaredPhysical == FieldType.DTO
                || declaredPhysical == FieldType.TEXT) {
            return UNBOUNDED;
        }
        return declared.getLength() == null ? UNKNOWN : declared.getLength();
    }

    private static int observedWidth(PhysicalColumn observed) {
        if (UNBOUNDED_JDBC_TYPES.contains(observed.jdbcType())) {
            return UNBOUNDED;
        }
        return observed.size() == null ? UNKNOWN : observed.size();
    }

    private static String width(int value) {
        if (value == UNBOUNDED) {
            return "(unbounded)";
        }
        return value == UNKNOWN ? "" : "(" + value + ")";
    }
}
