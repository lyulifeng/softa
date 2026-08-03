package io.softa.starter.metadata.ddl.introspect;

import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * A read-only snapshot of a database's physical shape — the fact side of the three-way
 * reconciliation (annotations decide the <i>target</i>, these facts decide the <i>verb</i>).
 * Produced by {@link PhysicalSchemaReader}; consumed by the DDL orchestrator's recovery
 * planning and by the studio JDBC reverse-engineering path.
 *
 * <p>All map/set keys are lower-cased at construction and every lookup lower-cases its
 * argument, so existence checks are insensitive to the engine's identifier-case folding
 * (MySQL platform-dependent, PostgreSQL lower, H2 upper). The original identifier case is
 * preserved on {@link PhysicalTable#name()} / {@link PhysicalColumn#name()} for consumers
 * that render names back out.
 *
 * @param tables physical tables keyed by lower-cased table name, in database iteration order
 */
public record PhysicalSchema(Map<String, PhysicalTable> tables) {

    /**
     * One physical table.
     *
     * @param name       the table name as reported by the database
     * @param columns    columns keyed by lower-cased column name, in ordinal position order
     * @param indexNames lower-cased names of the table's indexes; empty when the snapshot
     *                   was taken without index introspection
     */
    public record PhysicalTable(String name, Map<String, PhysicalColumn> columns, Set<String> indexNames) {
    }

    /**
     * One physical column.
     *
     * @param name     the column name as reported by the database
     * @param jdbcType the {@link java.sql.Types} code ({@code DatabaseMetaData.getColumns} DATA_TYPE)
     * @param size     COLUMN_SIZE (VARCHAR width / DECIMAL precision), or {@code null}
     * @param scale    DECIMAL_DIGITS, or {@code null}
     * @param nullable whether the column accepts NULL, or {@code null} when the driver
     *                 reports it as unknown
     */
    public record PhysicalColumn(String name, int jdbcType, Integer size, Integer scale, Boolean nullable) {
    }

    public boolean tableExists(String tableName) {
        return tables.containsKey(lower(tableName));
    }

    /** {@code false} when the table itself is missing. */
    public boolean columnExists(String tableName, String columnName) {
        PhysicalTable table = tables.get(lower(tableName));
        return table != null && table.columns().containsKey(lower(columnName));
    }

    /** The observed column, or {@code null} when the table or column is missing. */
    public PhysicalColumn column(String tableName, String columnName) {
        PhysicalTable table = tables.get(lower(tableName));
        return table == null ? null : table.columns().get(lower(columnName));
    }

    /** {@code false} when the table is missing or the snapshot skipped index introspection. */
    public boolean indexExists(String tableName, String indexName) {
        PhysicalTable table = tables.get(lower(tableName));
        return table != null && table.indexNames().contains(lower(indexName));
    }

    static String lower(String identifier) {
        return identifier == null ? null : identifier.toLowerCase(Locale.ROOT);
    }
}
