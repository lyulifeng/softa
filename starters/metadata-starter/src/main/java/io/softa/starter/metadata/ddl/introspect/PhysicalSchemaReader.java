package io.softa.starter.metadata.ddl.introspect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.sql.DataSource;

import io.softa.starter.metadata.ddl.SysDdlContextBuilder;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalTable;
import io.softa.starter.metadata.entity.SysModel;

/**
 * Reads a database's physical shape into a {@link PhysicalSchema} snapshot via
 * {@link DatabaseMetaData} — portable across engines, no per-DB SQL. The shared introspection
 * primitive behind two consumers with different scopes:
 * <ul>
 *   <li>the scanner-lane DDL orchestrator (managed tables only, with indexes) — physical facts
 *       for recovery planning;</li>
 *   <li>the studio JDBC reverse-engineering connector (all tables, no indexes) — raw material
 *       for {@code JdbcSchemaReader}'s model/field derivation.</li>
 * </ul>
 *
 * <p>Reads the connection's current catalog/schema, mirroring what the runtime's own
 * statements see. Table discovery is one {@code getTables} pass filtered in memory (robust
 * against per-driver pattern/case quirks); columns and indexes are then read per retained
 * table, so the cost is bounded by the requested scope, not the database size.
 */
public final class PhysicalSchemaReader {

    private PhysicalSchemaReader() {
    }

    /**
     * Snapshot the given models' tables (with indexes — the shape the DDL recovery and the
     * drift auditor both expect) from the data source's current catalog/schema. The connection
     * is borrowed and returned here.
     */
    public static PhysicalSchema readManagedTables(DataSource dataSource, List<SysModel> models)
            throws SQLException {
        Set<String> tables = models.stream()
                .map(SysDdlContextBuilder::resolveTableName)
                .map(t -> t.toLowerCase(Locale.ROOT))
                .collect(Collectors.toSet());
        try (Connection connection = dataSource.getConnection()) {
            return read(connection, tables, true);
        }
    }

    /**
     * Snapshot the connected catalog/schema.
     *
     * @param connection       an open connection; not closed by this method
     * @param tableNamesLower  lower-cased table names to retain, or {@code null} for all tables
     * @param withIndexes      whether to also read each retained table's index names
     *                         ({@code approximate=true}, no statistics cost)
     */
    public static PhysicalSchema read(Connection connection, Set<String> tableNamesLower, boolean withIndexes)
            throws SQLException {
        DatabaseMetaData meta = connection.getMetaData();
        String catalog = connection.getCatalog();
        String schema = connection.getSchema();
        Map<String, PhysicalTable> tables = new LinkedHashMap<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String key = PhysicalSchema.lower(tableName);
                if (tableNamesLower != null && !tableNamesLower.contains(key)) {
                    continue;
                }
                tables.put(key, new PhysicalTable(
                        tableName,
                        readColumns(meta, catalog, schema, tableName),
                        withIndexes ? readIndexNames(meta, catalog, schema, tableName) : Set.of()));
            }
        }
        return new PhysicalSchema(tables);
    }

    private static Map<String, PhysicalColumn> readColumns(
            DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        Map<String, PhysicalColumn> columns = new LinkedHashMap<>();
        try (ResultSet rs = meta.getColumns(catalog, schema, tableName, "%")) {
            while (rs.next()) {
                String columnName = rs.getString("COLUMN_NAME");
                columns.put(PhysicalSchema.lower(columnName), new PhysicalColumn(
                        columnName,
                        rs.getInt("DATA_TYPE"),
                        nullableInt(rs, "COLUMN_SIZE"),
                        nullableInt(rs, "DECIMAL_DIGITS"),
                        nullableFlag(rs)));
            }
        }
        return columns;
    }

    private static Set<String> readIndexNames(
            DatabaseMetaData meta, String catalog, String schema, String tableName) throws SQLException {
        Set<String> names = new LinkedHashSet<>();
        try (ResultSet rs = meta.getIndexInfo(catalog, schema, tableName, false, true)) {
            while (rs.next()) {
                String indexName = rs.getString("INDEX_NAME");
                if (indexName != null) {   // null on the tableIndexStatistic row
                    names.add(PhysicalSchema.lower(indexName));
                }
            }
        }
        return names;
    }

    private static Integer nullableInt(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }

    /** {@code IS_NULLABLE} is "YES"/"NO", or empty when the driver cannot tell. */
    private static Boolean nullableFlag(ResultSet rs) throws SQLException {
        String value = rs.getString("IS_NULLABLE");
        if ("YES".equalsIgnoreCase(value)) {
            return Boolean.TRUE;
        }
        if ("NO".equalsIgnoreCase(value)) {
            return Boolean.FALSE;
        }
        return null;
    }
}
