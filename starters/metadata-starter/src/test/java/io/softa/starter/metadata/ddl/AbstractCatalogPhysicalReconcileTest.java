package io.softa.starter.metadata.ddl;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.StorageType;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchemaReader;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2-backed end-to-end tests for {@link DdlOrchestrator#reconcilePhysical} — the
 * catalog-table boot path that converges a table's physical shape directly from its
 * from-code definition against introspected facts, with no catalog-row diff.
 *
 * <p>Facts are taken through the real {@link PhysicalSchemaReader}, so every scenario
 * exercises the same introspect→plan→execute loop the scanner runs at boot.
 *
 * <p>Fixtures model a miniature catalog table ({@code cat_probe}) rather than the real
 * {@code sys_*} entities: the planner is entity-agnostic (the scanner supplies parsed
 * catalog entities in production), and a synthetic model keeps every scenario's physical
 * precondition explicit in the test body.
 */
abstract class AbstractCatalogPhysicalReconcileTest {

    protected JdbcTemplate jdbcTemplate;
    protected DataSource dataSource;
    protected DdlOrchestrator orchestrator;

    protected abstract String h2JdbcUrl();

    protected abstract String productionJdbcUrl();

    @BeforeEach
    void setUp() {
        JdbcDataSource ds = new JdbcDataSource();
        ds.setURL(h2JdbcUrl());
        ds.setUser("sa");
        ds.setPassword("");
        this.dataSource = ds;
        this.jdbcTemplate = new JdbcTemplate(ds);
        this.orchestrator = new DdlOrchestrator(
                jdbcTemplate, BuiltinDdlMetadataResolver.INSTANCE, productionJdbcUrl());
    }

    // ---- fixtures -------------------------------------------------------

    private static SysModel probe() {
        SysModel m = new SysModel();
        m.setModelName("CatProbe");
        m.setLabel("CatProbe");
        m.setTableName("cat_probe");
        m.setIdStrategy(IdStrategy.DB_AUTO_ID);
        m.setStorageType(StorageType.RDBMS);
        m.setMultiTenant(false);
        m.setDescription("catalog reconcile probe");
        return m;
    }

    private static SysField field(String fieldName, FieldType type, Integer length) {
        SysField f = new SysField();
        f.setModelName("CatProbe");
        f.setFieldName(fieldName);
        f.setLabel(fieldName);
        f.setFieldType(type);
        if (length != null) {
            f.setLength(length);
        }
        f.setRequired(false);
        f.setDescription(fieldName);
        return f;
    }

    private static SysField idField() {
        SysField f = field("id", FieldType.LONG, null);
        f.setColumnName("id");
        f.setRequired(true);
        return f;
    }

    private static SysModelIndex idx(String indexName, List<String> fields, boolean unique) {
        SysModelIndex i = new SysModelIndex();
        i.setModelName("CatProbe");
        i.setIndexName(indexName);
        i.setIndexFields(new ArrayList<>(fields));
        i.setUniqueIndex(unique);
        return i;
    }

    /** Introspect through the production reader, then reconcile. */
    private boolean reconcile(List<SysField> fields, List<SysModelIndex> indexes) throws Exception {
        SysModel model = probe();
        PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, List.of(model));
        return orchestrator.reconcilePhysical(List.of(model), fields, indexes, facts);
    }

    // ---- genesis: table missing → CREATE, then idempotent no-op ----------

    @Test
    void missingTable_isCreated_thenSecondRunIsNoOp() throws Exception {
        List<SysField> fields = List.of(idField(),
                field("appCode", FieldType.STRING, 64),
                field("modelName", FieldType.STRING, 128));

        assertTrue(reconcile(fields, List.of()), "genesis must execute DDL");
        assertColumnExists("cat_probe", "app_code");
        assertColumnExists("cat_probe", "model_name");

        assertFalse(reconcile(fields, List.of()), "second run must plan nothing (idempotent)");
    }

    // ---- additive evolution: missing column → ADD --------------------------

    @Test
    void missingColumn_isAdded() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, app_code VARCHAR(64))");

        List<SysField> fields = List.of(idField(),
                field("appCode", FieldType.STRING, 64),
                field("autoSequence", FieldType.BOOLEAN, null));   // the V34 shape

        assertTrue(reconcile(fields, List.of()));
        assertColumnExists("cat_probe", "auto_sequence");
    }

    // ---- widen: physical narrower than declared → MODIFY --------------------

    @Test
    void narrowerPhysicalColumn_isWidened() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, description VARCHAR(256))");

        List<SysField> fields = List.of(idField(),
                field("description", FieldType.STRING, 512));   // the V33 shape

        assertTrue(reconcile(fields, List.of()));
        assertEquals(512, columnLength("cat_probe", "description"),
                "declared-wider column must be widened in place");
    }

    // ---- narrow: physical wider than declared → untouched, no unit ----------

    @Test
    void widerPhysicalColumn_isLeftUntouched() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, description VARCHAR(512))");

        List<SysField> fields = List.of(idField(),
                field("description", FieldType.STRING, 256));

        assertFalse(reconcile(fields, List.of()),
                "a would-be narrowing plans no unit at all — the drift audit is the channel");
        assertEquals(512, columnLength("cat_probe", "description"));
    }

    // ---- declared rename → CHANGE COLUMN, data carried -----------------------

    @Test
    void declaredRename_movesColumnAndCarriesData() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, soft_delete VARCHAR(64))");
        jdbcTemplate.execute("INSERT INTO cat_probe (id, soft_delete) VALUES (1, 'carried')");

        SysField renamed = field("softDeleteField", FieldType.STRING, 64);
        renamed.setRenamedFrom("softDelete");
        List<SysField> fields = List.of(idField(), renamed);

        assertTrue(reconcile(fields, List.of()));
        assertColumnExists("cat_probe", "soft_delete_field");
        assertColumnGone("cat_probe", "soft_delete");
        assertEquals("carried", jdbcTemplate.queryForObject(
                "SELECT soft_delete_field FROM cat_probe WHERE id = 1", String.class));
    }

    // ---- half-applied rename: both columns present → fail-fast ---------------

    @Test
    void halfAppliedRename_failsFast() {
        jdbcTemplate.execute("CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, "
                + "soft_delete VARCHAR(64), soft_delete_field VARCHAR(64))");

        SysField renamed = field("softDeleteField", FieldType.STRING, 64);
        renamed.setRenamedFrom("softDelete");
        List<SysField> fields = List.of(idField(), renamed);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> reconcile(fields, List.of()));
        assertTrue(e.getMessage().contains("soft_delete"),
                "the error must name the conflicting columns: " + e.getMessage());
    }

    // ---- orphan physical column: never dropped, never a unit -----------------

    @Test
    void undeclaredPhysicalColumn_isLeftAlone() throws Exception {
        jdbcTemplate.execute("CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, "
                + "app_code VARCHAR(64), legacy_leftover VARCHAR(64))");

        List<SysField> fields = List.of(idField(), field("appCode", FieldType.STRING, 64));

        assertFalse(reconcile(fields, List.of()), "an orphan column alone plans nothing");
        assertColumnExists("cat_probe", "legacy_leftover");
    }

    // ---- declared index missing → ADD INDEX ----------------------------------

    @Test
    void missingDeclaredIndex_isAdded() throws Exception {
        jdbcTemplate.execute(
                "CREATE TABLE cat_probe (id BIGINT NOT NULL PRIMARY KEY, model_name VARCHAR(128))");

        List<SysField> fields = List.of(idField(), field("modelName", FieldType.STRING, 128));
        List<SysModelIndex> indexes = List.of(idx("idx_cat_probe_model", List.of("modelName"), false));

        assertTrue(reconcile(fields, indexes));
        assertIndexExists("cat_probe", "idx_cat_probe_model");
    }

    // ---- helpers --------------------------------------------------------

    private void assertColumnExists(String table, String column) {
        assertEquals(1, columnCount(table, column), "column " + table + "." + column + " should exist");
    }

    private void assertColumnGone(String table, String column) {
        assertEquals(0, columnCount(table, column), "column " + table + "." + column + " should be gone");
    }

    private Integer columnCount(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)",
                Integer.class, table, column);
    }

    private Integer columnLength(String table, String column) {
        return jdbcTemplate.queryForObject(
                "SELECT CHARACTER_MAXIMUM_LENGTH FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = LOWER(?) AND LOWER(COLUMN_NAME) = LOWER(?)",
                Integer.class, table, column);
    }

    private void assertIndexExists(String table, String indexName) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT INDEX_NAME) FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE LOWER(TABLE_NAME) = LOWER(?) "
                        + "AND (LOWER(INDEX_NAME) = LOWER(?) OR LOWER(INDEX_NAME) LIKE LOWER(?))",
                Integer.class, table, indexName, indexName + "_index_%");
        assertNotNull(count);
        assertTrue(count >= 1, "index " + table + "." + indexName + " should exist");
    }
}
