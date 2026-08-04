package io.softa.starter.metadata.ddl.introspect;

import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.PreparedStatement;
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
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

import io.softa.starter.metadata.ddl.SysDdlContextBuilder;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalTable;
import io.softa.starter.metadata.entity.SysModel;

/**
 * Reads a database's physical shape into a {@link PhysicalSchema} snapshot via
 * {@link DatabaseMetaData} — portable across engines, no per-DB SQL for tables and columns.
 * The shared introspection primitive behind two consumers with different scopes:
 * <ul>
 *   <li>the scanner-lane DDL orchestrator (managed tables only, with indexes) — physical facts
 *       for recovery planning;</li>
 *   <li>the studio JDBC reverse-engineering connector (all tables, no indexes) — raw material
 *       for {@code JdbcSchemaReader}'s model/field derivation.</li>
 * </ul>
 *
 * <p>Reads the connection's current catalog/schema, mirroring what the runtime's own
 * statements see.
 *
 * <p><b>Round trips are constant, not per-table.</b> Tables and columns are each read in ONE
 * catalog-wide {@code DatabaseMetaData} pass and filtered/grouped in memory; index names come
 * from one dialect query where the dialect is known. A per-table loop (one
 * {@code getColumns} + one {@code getIndexInfo} each) is invisible on a local database and
 * catastrophic on a remote one — an app with a few hundred models turns into hundreds of
 * serial metadata round trips, and this snapshot runs on EVERY boot (the drift audit). The
 * in-memory filtering is the deliberate trade: more rows over the wire once, instead of
 * latency multiplied by table count.
 */
@Slf4j
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

        // Retained tables, keyed by lower-cased name → the driver-reported name (which the
        // per-dialect index query and the snapshot both need verbatim).
        Map<String, String> retained = new LinkedHashMap<>();
        try (ResultSet rs = meta.getTables(catalog, schema, "%", new String[]{"TABLE"})) {
            while (rs.next()) {
                String tableName = rs.getString("TABLE_NAME");
                String key = PhysicalSchema.lower(tableName);
                if (tableNamesLower != null && !tableNamesLower.contains(key)) {
                    continue;
                }
                retained.put(key, tableName);
            }
        }

        Map<String, Map<String, PhysicalColumn>> columns = readColumns(meta, catalog, schema, retained.keySet());
        Map<String, Set<String>> indexes = withIndexes
                ? readIndexNames(connection, meta, catalog, schema, retained)
                : Map.of();

        Map<String, PhysicalTable> tables = new LinkedHashMap<>();
        retained.forEach((key, name) -> tables.put(key, new PhysicalTable(
                name,
                columns.getOrDefault(key, Map.of()),
                indexes.getOrDefault(key, Set.of()))));
        return new PhysicalSchema(tables);
    }

    /** All columns of the catalog/schema in one pass, grouped by table, out-of-scope tables dropped. */
    private static Map<String, Map<String, PhysicalColumn>> readColumns(
            DatabaseMetaData meta, String catalog, String schema, Set<String> retainedKeys) throws SQLException {
        Map<String, Map<String, PhysicalColumn>> byTable = new LinkedHashMap<>();
        if (retainedKeys.isEmpty()) {
            return byTable;
        }
        try (ResultSet rs = meta.getColumns(catalog, schema, "%", "%")) {
            while (rs.next()) {
                String tableKey = PhysicalSchema.lower(rs.getString("TABLE_NAME"));
                if (!retainedKeys.contains(tableKey)) {
                    continue;
                }
                String columnName = rs.getString("COLUMN_NAME");
                byTable.computeIfAbsent(tableKey, key -> new LinkedHashMap<>())
                        .put(PhysicalSchema.lower(columnName), new PhysicalColumn(
                                columnName,
                                rs.getInt("DATA_TYPE"),
                                nullableInt(rs, "COLUMN_SIZE"),
                                nullableInt(rs, "DECIMAL_DIGITS"),
                                nullableFlag(rs)));
            }
        }
        return byTable;
    }

    /**
     * Index names per retained table. Prefers one dialect query; falls back to a
     * {@code getIndexInfo} call per table for unknown dialects (portable, but one round trip
     * each — acceptable as the fallback, never the default).
     */
    private static Map<String, Set<String>> readIndexNames(
            Connection connection, DatabaseMetaData meta, String catalog, String schema,
            Map<String, String> retained) throws SQLException {
        if (retained.isEmpty()) {
            return Map.of();
        }
        BatchIndexQuery query = batchIndexQuery(meta, catalog, schema);
        if (query != null) {
            Map<String, Set<String>> batched = readIndexNamesBatch(connection, query, retained.keySet());
            // Empty across every retained table means the scope predicate did not match what
            // the driver reports as catalog/schema (every managed table has at least a primary
            // key). Trusting it would report the whole catalog's indexes as missing.
            if (!batched.isEmpty()) {
                return batched;
            }
            log.warn("PhysicalSchemaReader: batched index query returned nothing for {} table(s); "
                    + "falling back to per-table introspection", retained.size());
        }
        Map<String, Set<String>> byTable = new LinkedHashMap<>();
        for (Map.Entry<String, String> table : retained.entrySet()) {
            byTable.put(table.getKey(), readIndexNamesPerTable(meta, catalog, schema, table.getValue()));
        }
        return byTable;
    }

    /** One dialect query returning {@code (table_name, index_name)} for the whole scope. */
    private record BatchIndexQuery(String sql, String scope) {
    }

    private static BatchIndexQuery batchIndexQuery(DatabaseMetaData meta, String catalog, String schema)
            throws SQLException {
        String product = StringUtils.lowerCase(meta.getDatabaseProductName());
        if (product == null) {
            return null;
        }
        // MySQL and its wire-compatible kin scope by `table_schema`, which their drivers report
        // as the catalog; PostgreSQL scopes by schema. A blank scope value would silently match
        // nothing — take the portable fallback instead.
        if (product.contains("mysql") || product.contains("mariadb") || product.contains("tidb")) {
            return StringUtils.isBlank(catalog) ? null : new BatchIndexQuery(
                    "SELECT table_name, index_name FROM information_schema.statistics WHERE table_schema = ?",
                    catalog);
        }
        if (product.contains("postgresql")) {
            return StringUtils.isBlank(schema) ? null : new BatchIndexQuery(
                    "SELECT tablename AS table_name, indexname AS index_name FROM pg_indexes WHERE schemaname = ?",
                    schema);
        }
        return null;
    }

    private static Map<String, Set<String>> readIndexNamesBatch(
            Connection connection, BatchIndexQuery query, Set<String> retainedKeys) throws SQLException {
        Map<String, Set<String>> byTable = new LinkedHashMap<>();
        try (PreparedStatement statement = connection.prepareStatement(query.sql())) {
            statement.setString(1, query.scope());
            try (ResultSet rs = statement.executeQuery()) {
                while (rs.next()) {
                    String tableKey = PhysicalSchema.lower(rs.getString("table_name"));
                    if (!retainedKeys.contains(tableKey)) {
                        continue;
                    }
                    String indexName = rs.getString("index_name");
                    if (indexName != null) {
                        byTable.computeIfAbsent(tableKey, key -> new LinkedHashSet<>())
                                .add(PhysicalSchema.lower(indexName));
                    }
                }
            }
        }
        return byTable;
    }

    private static Set<String> readIndexNamesPerTable(
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
