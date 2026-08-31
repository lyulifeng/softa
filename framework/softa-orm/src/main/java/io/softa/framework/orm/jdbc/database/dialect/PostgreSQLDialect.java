package io.softa.framework.orm.jdbc.database.dialect;

import java.util.EnumMap;
import java.util.Map;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.utils.Assert;

/**
 * PostgreSQL Dialect
 *
 * <p><b>Pattern-matching operator depends on the column collation.</b> PostgreSQL compares
 * case-sensitively by default, so the pattern operators map to {@code ILIKE} to reproduce what
 * MySQL gives for free under a {@code _ci} collation. That inverts once the string columns
 * carry a <i>nondeterministic</i> ICU collation (the migration path away from MySQL's
 * {@code utf8mb4_unicode_ci}): {@code LIKE} is then already case-insensitive, and {@code ILIKE}
 * is outright rejected — {@code ERROR: nondeterministic collations are not supported for ILIKE}.
 * Regular expressions and {@code starts_with()} are refused on those columns too, which is why
 * this dialect only ever emits {@code LIKE}.
 *
 * <p>{@code caseInsensitiveCollation} therefore selects between the two, and defaults to
 * {@code false} so an existing deployment keeps the {@code ILIKE} behavior it has today.
 * Turn it on by setting {@code system.metadata.ddl.postgres-string-collation}, which is the
 * same switch that makes the DDL generator stamp {@code COLLATE} onto string columns — the two
 * halves are one decision and must not be enabled separately: collation without this emits
 * {@code ILIKE} against ci columns and every search errors; this without collation silently
 * turns every search case-sensitive.
 */
public class PostgreSQLDialect implements DialectInterface {

    /** Operators whose predicate is a pattern match, i.e. the ones the collation decides. */
    private static final Operator[] PATTERN_OPERATORS = {
            Operator.CONTAINS, Operator.NOT_CONTAINS,
            Operator.START_WITH, Operator.NOT_START_WITH,
            Operator.CHILD_OF};

    private static final Map<Operator, String> BASE_OPERATOR_MAP = new EnumMap<>(Operator.class);

    static {
        BASE_OPERATOR_MAP.put(Operator.EQUAL, "=");
        BASE_OPERATOR_MAP.put(Operator.NOT_EQUAL, "!=");
        BASE_OPERATOR_MAP.put(Operator.GREATER_THAN, ">");
        BASE_OPERATOR_MAP.put(Operator.GREATER_THAN_OR_EQUAL, ">=");
        BASE_OPERATOR_MAP.put(Operator.LESS_THAN, "<");
        BASE_OPERATOR_MAP.put(Operator.LESS_THAN_OR_EQUAL, "<=");
        BASE_OPERATOR_MAP.put(Operator.IN, "IN");
        BASE_OPERATOR_MAP.put(Operator.NOT_IN, "NOT IN");
        BASE_OPERATOR_MAP.put(Operator.BETWEEN, "BETWEEN ? AND ?");
        BASE_OPERATOR_MAP.put(Operator.NOT_BETWEEN, "NOT BETWEEN ? AND ?");
        BASE_OPERATOR_MAP.put(Operator.IS_SET, "IS NOT NULL");
        BASE_OPERATOR_MAP.put(Operator.IS_NOT_SET, "IS NULL");
        BASE_OPERATOR_MAP.put(Operator.PARENT_OF, "IN");
    }

    private final Map<Operator, String> operatorMap;

    /** Default posture: case-sensitive columns, pattern matching via {@code ILIKE}. */
    public PostgreSQLDialect() {
        this(false);
    }

    /**
     * @param caseInsensitiveCollation whether the string columns carry a nondeterministic
     *     (case-insensitive) collation, in which case pattern matching must use {@code LIKE}
     */
    public PostgreSQLDialect(boolean caseInsensitiveCollation) {
        Map<Operator, String> map = new EnumMap<>(BASE_OPERATOR_MAP);
        String patternPredicate = caseInsensitiveCollation ? "LIKE" : "ILIKE";
        for (Operator operator : PATTERN_OPERATORS) {
            boolean negated = operator == Operator.NOT_CONTAINS || operator == Operator.NOT_START_WITH;
            map.put(operator, negated ? "NOT " + patternPredicate : patternPredicate);
        }
        this.operatorMap = map;
    }

    /**
     * Get the predicate of the database query operator: >, =, IN, etc.
     *
     * @param operator FilterUnit operator
     * @return SQL operator predicate string
     */
    public String getPredicate(Operator operator) {
        Assert.isTrue(operatorMap.containsKey(operator), """
                Predicate conversion for operator {0} is missing in {1}.
                Check whether the operator is existed in the Operator Enum class.
                """, operator.getName(), this.getClass().getSimpleName());
        return operatorMap.get(operator);
    }

    /**
     * Get the database paging clause according to the limit and offset.
     *
     * @param limit limit
     * @param offset offset
     * @return paging clause
     */
    public StringBuilder getPageClause(int limit, int offset) {
        return new StringBuilder(" LIMIT ").append(limit).append(" OFFSET ").append(offset);
    }
}
