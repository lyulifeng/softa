package io.softa.starter.metadata.ddl.introspect;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.SQLException;
import java.sql.Types;
import java.util.Set;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.starter.metadata.ddl.introspect.PhysicalSchema.PhysicalColumn;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2-backed tests for {@link PhysicalSchemaReader}: table filtering, identifier-case
 * normalization (H2 folds unquoted names to UPPER — the snapshot must still answer
 * lower/mixed-case lookups), column facts, and optional index introspection.
 */
class PhysicalSchemaReaderTest {

    private JdbcDataSource dataSource;
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:physical_schema_reader;DB_CLOSE_DELAY=-1;DATABASE_TO_UPPER=true");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        jdbcTemplate.execute("DROP ALL OBJECTS");
        jdbcTemplate.execute("""
                CREATE TABLE customer
                (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(64), balance DECIMAL(10,2))
                """);
        jdbcTemplate.execute("CREATE UNIQUE INDEX uk_customer_email ON customer (email)");
        jdbcTemplate.execute("CREATE TABLE unrelated (id BIGINT NOT NULL PRIMARY KEY)");
    }

    private PhysicalSchema read(Set<String> tables, boolean withIndexes) throws SQLException {
        try (Connection connection = dataSource.getConnection()) {
            return PhysicalSchemaReader.read(connection, tables, withIndexes);
        }
    }

    @Test
    void readsColumnsWithCaseInsensitiveLookups() throws SQLException {
        PhysicalSchema schema = read(Set.of("customer"), false);

        // H2 reports CUSTOMER/EMAIL — the snapshot must answer any-case lookups.
        assertTrue(schema.tableExists("customer"));
        assertTrue(schema.tableExists("CUSTOMER"));
        assertTrue(schema.columnExists("customer", "email"));
        assertTrue(schema.columnExists("Customer", "Email"));
        assertFalse(schema.columnExists("customer", "vanished"));

        PhysicalColumn balance = schema.tables().get("customer").columns().get("balance");
        assertNotNull(balance);
        assertTrue(balance.jdbcType() == Types.DECIMAL || balance.jdbcType() == Types.NUMERIC,
                "DECIMAL(10,2) should report an exact-numeric JDBC type, got " + balance.jdbcType());
        assertEquals(10, balance.size());
        assertEquals(2, balance.scale());
        assertEquals(Boolean.FALSE, schema.tables().get("customer").columns().get("id").nullable());
    }

    @Test
    void filterRetainsOnlyRequestedTables() throws SQLException {
        PhysicalSchema schema = read(Set.of("customer"), false);

        assertTrue(schema.tableExists("customer"));
        assertFalse(schema.tableExists("unrelated"), "out-of-filter tables must not be read");

        PhysicalSchema all = read(null, false);
        assertTrue(all.tableExists("unrelated"), "null filter means all tables");
    }

    @Test
    void indexIntrospectionIsOptional() throws SQLException {
        PhysicalSchema withIndexes = read(Set.of("customer"), true);
        assertTrue(withIndexes.indexExists("customer", "uk_customer_email"));
        assertTrue(withIndexes.indexExists("CUSTOMER", "UK_CUSTOMER_EMAIL"));
        assertFalse(withIndexes.indexExists("customer", "idx_nope"));

        PhysicalSchema withoutIndexes = read(Set.of("customer"), false);
        assertFalse(withoutIndexes.indexExists("customer", "uk_customer_email"),
                "a snapshot taken without index introspection answers false, never throws");
    }

    @Test
    void missingTableAnswersFalseEverywhere() throws SQLException {
        PhysicalSchema schema = read(Set.of("customer"), true);

        assertFalse(schema.tableExists("dropped_by_hand"));
        assertFalse(schema.columnExists("dropped_by_hand", "id"));
        assertFalse(schema.indexExists("dropped_by_hand", "uk_x"));
    }

    /**
     * The round-trip contract: columns are read in ONE metadata pass no matter how many tables
     * are in scope. A per-table loop is invisible locally and pathological against a remote
     * database (hundreds of serial round trips on every boot), so the count is asserted, not
     * just the result.
     */
    @Test
    void columnsAreReadInOneMetadataPassRegardlessOfTableCount() throws SQLException {
        jdbcTemplate.execute("CREATE TABLE extra_one (id BIGINT NOT NULL PRIMARY KEY, note VARCHAR(10))");
        jdbcTemplate.execute("CREATE TABLE extra_two (id BIGINT NOT NULL PRIMARY KEY, note VARCHAR(10))");

        CallCounter counter = new CallCounter();
        try (Connection connection = dataSource.getConnection()) {
            PhysicalSchema schema = PhysicalSchemaReader.read(
                    counter.wrap(connection), Set.of("customer", "extra_one", "extra_two"), false);

            assertEquals(3, schema.tables().size());
            assertEquals(1, counter.getColumnsCalls,
                    "columns must be read in a single catalog-wide pass, not once per table");
            // Grouping must not leak same-named columns across tables.
            assertTrue(schema.columnExists("extra_one", "note"));
            assertTrue(schema.columnExists("extra_two", "note"));
            assertFalse(schema.columnExists("customer", "note"));
        }
    }

    /** Counts the metadata calls a read performs, delegating everything else. */
    private static final class CallCounter {
        private int getColumnsCalls;

        Connection wrap(Connection delegate) {
            return (Connection) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{Connection.class},
                    (proxy, method, args) -> {
                        Object result = invoke(delegate, method, args);
                        return "getMetaData".equals(method.getName())
                                ? wrapMetaData((DatabaseMetaData) result) : result;
                    });
        }

        private DatabaseMetaData wrapMetaData(DatabaseMetaData delegate) {
            return (DatabaseMetaData) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{DatabaseMetaData.class},
                    (proxy, method, args) -> {
                        if ("getColumns".equals(method.getName())) {
                            getColumnsCalls++;
                        }
                        return invoke(delegate, method, args);
                    });
        }

        private static Object invoke(Object target, Method method, Object[] args) throws Throwable {
            try {
                return method.invoke(target, args);
            } catch (InvocationTargetException e) {
                throw e.getCause();
            }
        }
    }
}
