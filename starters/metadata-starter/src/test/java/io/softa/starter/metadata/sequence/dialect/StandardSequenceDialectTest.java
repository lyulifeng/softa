package io.softa.starter.metadata.sequence.dialect;

import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.jdbc.JdbcProxy;
import io.softa.framework.orm.jdbc.database.SqlParams;

import static org.junit.jupiter.api.Assertions.*;

/**
 * {@link StandardSequenceDialect}'s statements must express NULL-safe equality as
 * {@code IS NOT DISTINCT FROM ?} — never as {@code a = ? OR (a IS NULL AND ? IS NULL)}.
 *
 * <p>The expanded form binds the same value twice and leaves the second occurrence inside a
 * bare {@code ? IS NULL}, which PostgreSQL cannot type at prepare time: every allocation fails
 * with {@code 42P18 could not determine data type of parameter}, i.e. every create that draws
 * an auto-generated code breaks on PostgreSQL. H2 (and MySQL) accept the expanded form, so the
 * semantic tests below would keep passing after a regression — the shape guard is what protects
 * the PostgreSQL prepare path, the same posture as
 * {@code SysJdbcWriterSequenceAdvisoryTest}'s boolean-literal guard.
 *
 * <p>The semantic tests execute the real statements against H2 and pin the behaviors the
 * NULL-safe predicate exists for: a {@code last_reset_key IS NULL} row still advances, a
 * changed reset key restarts from {@code startValue}, and a mismatched tenant matches nothing.
 */
class StandardSequenceDialectTest {

    // ---- shape guard (protects the PostgreSQL prepare path) ---------------

    @Test
    void allStatementsUseIsNotDistinctFrom_andNeverABareParameterIsNull() {
        for (String constant : new String[]{"SQL_SINGLE", "SQL_BATCH", "SQL_SELECT_CURRENT"}) {
            String sql = (String) ReflectionTestUtils.getField(StandardSequenceDialect.class, constant);
            assertNotNull(sql, constant);
            assertTrue(sql.contains("IS NOT DISTINCT FROM ?"), constant + ":\n" + sql);
            assertFalse(sql.matches("(?is).*\\?\\s+IS\\s+NULL.*"),
                    constant + " reverted to the expanded NULL-safe form, which fails to prepare "
                            + "on PostgreSQL (42P18):\n" + sql);
        }
    }

    // ---- semantics against H2 ---------------------------------------------

    private JdbcTemplate jdbcTemplate;
    private StandardSequenceDialect dialect;

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL("jdbc:h2:mem:seq_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        ds.setUser("sa");
        ds.setPassword("");
        this.jdbcTemplate = new JdbcTemplate(ds);
        jdbcTemplate.execute("""
                CREATE TABLE sys_sequence (
                  id BIGINT PRIMARY KEY,
                  tenant_id BIGINT,
                  current_value BIGINT NOT NULL,
                  last_reset_key VARCHAR(64),
                  updated_time TIMESTAMP
                )
                """);
        JdbcProxy jdbcProxy = new JdbcProxy();
        ReflectionTestUtils.setField(jdbcProxy, "jdbcTemplate", jdbcTemplate);
        this.dialect = new StandardSequenceDialect(jdbcProxy);
    }

    private void row(long id, Long tenantId, long currentValue, String lastResetKey) {
        jdbcTemplate.update("INSERT INTO sys_sequence (id, tenant_id, current_value, last_reset_key) "
                + "VALUES (?, ?, ?, ?)", id, tenantId, currentValue, lastResetKey);
    }

    private int allocate(String currentKey, long step, long startValue, int count, Long id, Long tenantId) {
        SqlParams sp = dialect.buildAllocateSql(currentKey, step, startValue, count, id, tenantId);
        return jdbcTemplate.update(sp.getSql(), sp.getArgsArray());
    }

    private long currentValue(long id) {
        return jdbcTemplate.queryForObject("SELECT current_value FROM sys_sequence WHERE id = ?",
                Long.class, id);
    }

    @Test
    void sameResetKey_advancesByStep() {
        row(1L, 7L, 10L, "2026-09");
        assertEquals(1, allocate("2026-09", 1, 100, 1, 1L, 7L));
        assertEquals(11L, currentValue(1L));
        assertEquals(11L, dialect.fetchEndValue("SysSequence", 1L, 7L));
    }

    @Test
    void changedResetKey_restartsFromStartValue() {
        row(1L, 7L, 55L, "2026-09");
        assertEquals(1, allocate("2026-10", 1, 100, 1, 1L, 7L));
        assertEquals(100L, currentValue(1L));
        assertEquals("2026-10", jdbcTemplate.queryForObject(
                "SELECT last_reset_key FROM sys_sequence WHERE id = 1", String.class));
    }

    @Test
    void nullResetKey_matchesNullAndAdvances() {
        // The case the NULL-safe predicate exists for: a sequence with no reset period keeps
        // last_reset_key NULL forever, and NULL must compare equal to the NULL parameter.
        row(1L, null, 10L, null);
        assertEquals(1, allocate(null, 1, 100, 1, 1L, null));
        assertEquals(11L, currentValue(1L));
        assertEquals(11L, dialect.fetchEndValue("SysSequence", 1L, null));
    }

    @Test
    void mismatchedTenant_matchesNothing() {
        row(1L, 7L, 10L, null);
        assertEquals(0, allocate(null, 1, 100, 1, 1L, 8L));
        assertEquals(0, allocate(null, 1, 100, 1, 1L, null),
                "NULL tenant parameter must not match a tenant-owned row");
        assertEquals(10L, currentValue(1L));
    }

    @Test
    void batchAllocation_advancesAndRestartsWithBatchArithmetic() {
        row(1L, null, 10L, "2026-09");
        assertEquals(1, allocate("2026-09", 2, 1, 5, 1L, null));
        assertEquals(20L, currentValue(1L), "same key: current + count * step");

        assertEquals(1, allocate("2026-10", 2, 1, 5, 1L, null));
        assertEquals(9L, currentValue(1L), "changed key: startValue + (count - 1) * step");
    }
}
