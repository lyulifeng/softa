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
import io.softa.starter.metadata.entity.*;
import io.softa.starter.metadata.scanner.diff.SchemaDiff;
import io.softa.starter.metadata.scanner.diff.SchemaDiff.EntityDiff;
import io.softa.starter.metadata.scanner.diff.SchemaDiff.Modification;

import static org.junit.jupiter.api.Assertions.*;

/**
 * H2-backed end-to-end tests for {@link DdlOrchestrator#converge} — the scanner's primary
 * DDL lane wherever {@code scanner-scope} is active: the physical schema of every in-scope
 * owned table converges to the from-code definition on every boot, destructive verbs
 * included (undeclared columns / indexes drop, narrowing and type-family mismatches modify
 * to the declared shape). The {@link SchemaDiff} contributes only what introspection cannot
 * see: rename pairings, attribute deltas, index-definition changes.
 *
 * <p>Facts are taken through the real {@link PhysicalSchemaReader}, so every scenario
 * exercises the same introspect→plan→execute loop the scanner runs at boot.
 *
 * <p>Pre-setup SQL uses ANSI-compatible forms so it works in both MySQL and PostgreSQL H2
 * modes; do not introduce vendor-specific syntax here.
 */
abstract class AbstractDdlConvergeTest {

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

    private static SysModel customer() {
        SysModel m = new SysModel();
        m.setModelName("Customer");
        m.setLabel("Customer");
        m.setTableName("customer");
        m.setIdStrategy(IdStrategy.DB_AUTO_ID);
        m.setStorageType(StorageType.RDBMS);
        m.setMultiTenant(false);
        m.setDescription("Customer master");
        return m;
    }

    private static SysField field(String fieldName, FieldType type, Integer length, boolean required) {
        return fieldWithColumn(fieldName, fieldName, type, length, required);
    }

    private static SysField fieldWithColumn(String fieldName, String columnName,
                                            FieldType type, Integer length, boolean required) {
        SysField f = new SysField();
        f.setModelName("Customer");
        f.setFieldName(fieldName);
        f.setColumnName(columnName);
        f.setLabel(fieldName);
        f.setFieldType(type);
        if (length != null) {
            f.setLength(length);
        }
        f.setRequired(required);
        f.setDescription(fieldName);
        return f;
    }

    private static SysField idField() {
        SysField f = field("id", FieldType.LONG, null, true);
        f.setDescription("ID");
        return f;
    }

    private static SysModelIndex idx(String indexName, List<String> fields, boolean unique) {
        SysModelIndex i = new SysModelIndex();
        i.setModelName("Customer");
        i.setIndexName(indexName);
        i.setIndexFields(new ArrayList<>(fields));
        i.setUniqueIndex(unique);
        return i;
    }

    // ---- helpers ---------------------------------------------------------

    /** Introspect through the production reader, then converge with the given diff. */
    private boolean converge(List<SysModel> models, List<SysField> fields,
                             List<SysModelIndex> indexes, SchemaDiff diff) throws Exception {
        ReferenceColumnResolver.stampSysFields(fields);
        PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, models);
        return orchestrator.converge(models, fields, indexes, diff, facts);
    }

    private boolean converge(List<SysModel> models, List<SysField> fields,
                             List<SysModelIndex> indexes) throws Exception {
        return converge(models, fields, indexes, SchemaDiff.empty());
    }

    private static SchemaDiff diffWithModifiedField(SysField fromCode, SysField fromDb, SchemaDiff.Kind kind) {
        return new SchemaDiff(
                EntityDiff.<SysModel>empty(),
                new EntityDiff<>(List.of(), List.of(), List.of(new Modification<>(fromCode, fromDb, kind))),
                EntityDiff.<SysOptionSet>empty(),
                EntityDiff.<SysOptionItem>empty());
    }

    private static SchemaDiff diffWithModifiedModel(SysModel fromCode, SysModel fromDb, SchemaDiff.Kind kind) {
        return new SchemaDiff(
                new EntityDiff<>(List.of(), List.of(), List.of(new Modification<>(fromCode, fromDb, kind))),
                EntityDiff.<SysField>empty(),
                EntityDiff.<SysOptionSet>empty(),
                EntityDiff.<SysOptionItem>empty());
    }

    // ---- drift healing: missing declared column, no diff at all ----------

    @Test
    void missingDeclaredColumn_isAdded_withoutAnyDiff() throws Exception {
        // The UAT wound this lane exists for: sys_* and code agree (empty diff), but the
        // physical table lags — a partial restore, a cross-env sys_* import. Convergence
        // heals it on the next boot instead of leaving the model broken at runtime.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY)");

        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 128, false));
        assertTrue(converge(List.of(customer()), fields, List.of()), "healing must execute DDL");
        assertColumnExists("customer", "email");

        assertFalse(converge(List.of(customer()), fields, List.of()),
                "second run must plan nothing (idempotent)");
    }

    // ---- drift elimination: undeclared column dropped ---------------------

    @Test
    void undeclaredColumn_isDropped() throws Exception {
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, "
                + "email VARCHAR(64), legacy_leftover VARCHAR(64))");

        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 64, false));
        assertTrue(converge(List.of(customer()), fields, List.of()));

        assertColumnExists("customer", "email");
        assertColumnGone("customer", "legacy_leftover");
    }

    @Test
    void undeclaredColumnDrop_alreadyGone_degradesInsteadOfFailing() throws Exception {
        // Stale facts: the snapshot saw the column, someone dropped it before execution.
        // The DROP degrades to already-applied instead of failing the boot.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, stale VARCHAR(8))");
        List<SysModel> models = List.of(customer());
        List<SysField> fields = List.of(idField());
        ReferenceColumnResolver.stampSysFields(fields);
        PhysicalSchema staleFacts = PhysicalSchemaReader.readManagedTables(dataSource, models);

        jdbcTemplate.execute("ALTER TABLE customer DROP COLUMN stale");

        assertDoesNotThrow(() ->
                orchestrator.converge(models, fields, List.of(), SchemaDiff.empty(), staleFacts));
        assertColumnGone("customer", "stale");
    }

    // ---- drift elimination: shape converges to the declaration ------------

    @Test
    void widerPhysicalColumn_isNarrowedToDeclaredWidth() throws Exception {
        // The declaration is the truth and the environment is by definition non-production:
        // unlike the metadata-only lane, a physically wider column converges down.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(256))");

        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 64, false));
        assertTrue(converge(List.of(customer()), fields, List.of()));
        assertEquals(64, columnLength("customer", "email"));

        assertFalse(converge(List.of(customer()), fields, List.of()),
                "converged shape must not re-plan (no flapping)");
    }

    @Test
    void typeFamilyDrift_convergesToDeclaredType() throws Exception {
        // Physical BIGINT vs declared INTEGER — a narrowing across the numeric lattice
        // that the audit reports as drift; the convergence lane executes it.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, points BIGINT)");

        List<SysField> fields = List.of(idField(), field("points", FieldType.INTEGER, null, false));
        assertTrue(converge(List.of(customer()), fields, List.of()));

        String type = jdbcTemplate.queryForObject(
                "SELECT DATA_TYPE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = 'customer' AND LOWER(COLUMN_NAME) = 'points'",
                String.class);
        assertNotNull(type);
        assertTrue(type.toUpperCase().contains("INT") && !type.toUpperCase().contains("BIGINT"),
                "column must converge to the declared INTEGER, got " + type);
    }

    // ---- attribute deltas ride the diff, not the facts ---------------------

    @Test
    void attributeModify_executesEvenWhenPhysicalShapeIsEqual() throws Exception {
        // required=false → true is invisible to the type comparison; the diff carries it.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(64))");

        SysField before = field("email", FieldType.STRING, 64, false);
        SysField after = field("email", FieldType.STRING, 64, true);
        List<SysField> fields = List.of(idField(), after);
        assertTrue(converge(List.of(customer()), fields, List.of(),
                diffWithModifiedField(after, before, SchemaDiff.Kind.MODIFY)));

        String nullable = jdbcTemplate.queryForObject(
                "SELECT IS_NULLABLE FROM INFORMATION_SCHEMA.COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = 'customer' AND LOWER(COLUMN_NAME) = 'email'",
                String.class);
        assertEquals("NO", nullable, "the diff-carried NOT NULL must execute");
    }

    @Test
    void attributeModify_alsoCarriesTheDriftInOneUnit() throws Exception {
        // The same column has BOTH a declaration change (required) and physical width
        // drift: the diff-driven MODIFY re-states the full declared shape, so no second
        // drift unit is needed — width and nullability converge together.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(256))");

        SysField before = field("email", FieldType.STRING, 64, false);
        SysField after = field("email", FieldType.STRING, 64, true);
        List<SysField> fields = List.of(idField(), after);
        assertTrue(converge(List.of(customer()), fields, List.of(),
                diffWithModifiedField(after, before, SchemaDiff.Kind.MODIFY)));

        assertEquals(64, columnLength("customer", "email"));
    }

    // ---- indexes -----------------------------------------------------------

    @Test
    void missingDeclaredIndex_isAdded_andUndeclaredIndexIsDropped() throws Exception {
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, "
                + "email VARCHAR(64), phone VARCHAR(32))");
        jdbcTemplate.execute("CREATE INDEX idx_hand_made ON customer (phone)");

        List<SysField> fields = List.of(idField(),
                field("email", FieldType.STRING, 64, false),
                field("phone", FieldType.STRING, 32, false));
        List<SysModelIndex> indexes = List.of(idx("idx_customer_email", List.of("email"), false));
        assertTrue(converge(List.of(customer()), fields, indexes));

        assertIndexExists("customer", "idx_customer_email");
        assertIndexGone("customer", "idx_hand_made");
    }

    @Test
    void primaryKeyBackingIndex_isNeverDropped_andCleanSchemaPlansNothing() throws Exception {
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(64))");

        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 64, false));
        assertFalse(converge(List.of(customer()), fields, List.of()),
                "a clean schema (PK index included) must plan nothing");
    }

    @Test
    void declaredUniqueIndex_engineMangledName_doesNotFlap() throws Exception {
        // H2 reports a unique index created as uk_x under uk_x_INDEX_<n>. The shared name
        // matcher must treat that as the declared index — neither "missing" (re-add) nor
        // "undeclared" (drop) — or a converging boot would churn it forever.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, email VARCHAR(64))");

        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 64, false));
        List<SysModelIndex> indexes = List.of(idx("uk_customer_email", List.of("email"), true));
        assertTrue(converge(List.of(customer()), fields, indexes), "first run adds the index");
        assertIndexExists("customer", "uk_customer_email");

        assertFalse(converge(List.of(customer()), fields, indexes),
                "second run must plan nothing — mangled unique-index names must not flap");
    }

    @Test
    void indexDefinitionChange_isRebuilt() throws Exception {
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, "
                + "email VARCHAR(64), phone VARCHAR(32))");
        jdbcTemplate.execute("CREATE INDEX idx_customer_email ON customer (email)");

        List<SysField> fields = List.of(idField(),
                field("email", FieldType.STRING, 64, false),
                field("phone", FieldType.STRING, 32, false));
        SysModelIndex before = idx("idx_customer_email", List.of("email"), false);
        SysModelIndex after = idx("idx_customer_email", List.of("email", "phone"), false);
        SchemaDiff diff = new SchemaDiff(
                EntityDiff.<SysModel>empty(),
                EntityDiff.<SysField>empty(),
                EntityDiff.<SysOptionSet>empty(),
                EntityDiff.<SysOptionItem>empty(),
                new EntityDiff<>(List.of(), List.of(), List.of(new Modification<>(after, before))));
        assertTrue(converge(List.of(customer()), fields, List.of(after), diff));

        Integer columns = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.INDEX_COLUMNS "
                        + "WHERE LOWER(TABLE_NAME) = 'customer' AND LOWER(INDEX_NAME) = 'idx_customer_email'",
                Integer.class);
        assertEquals(2, columns, "rebuilt index must carry the new two-column definition");
    }

    // ---- renames ------------------------------------------------------------

    @Test
    void declaredRename_viaDiffPairing_movesColumnAndCarriesData() throws Exception {
        // The diff pairing carries the exact prior column name — custom columnName included.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, acct VARCHAR(100))");
        jdbcTemplate.execute("INSERT INTO customer (id, acct) VALUES (1, 'A-001')");

        SysField oldField = fieldWithColumn("acctNo", "acct", FieldType.STRING, 100, false);
        SysField newField = fieldWithColumn("accountNumber", "account_number", FieldType.STRING, 100, false);
        List<SysField> fields = List.of(idField(), newField);
        assertTrue(converge(List.of(customer()), fields, List.of(),
                diffWithModifiedField(newField, oldField, SchemaDiff.Kind.RENAME)));

        assertColumnExists("customer", "account_number");
        assertColumnGone("customer", "acct");
        assertEquals("A-001", jdbcTemplate.queryForObject(
                "SELECT account_number FROM customer WHERE id = 1", String.class));
    }

    @Test
    void declaredRename_viaCodeAttribute_healsAfterRowsMovedOn() throws Exception {
        // The rows were already reconciled on some other database (empty diff here), but
        // this physical schema still carries the prior column: the code-side renamedFrom
        // pairs them, so the drift heals as a CHANGE instead of divorcing into ADD + DROP.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, acct_no VARCHAR(100))");
        jdbcTemplate.execute("INSERT INTO customer (id, acct_no) VALUES (1, 'carried')");

        SysField renamed = fieldWithColumn("accountNumber", "account_number", FieldType.STRING, 100, false);
        renamed.setRenamedFrom("acctNo");
        List<SysField> fields = List.of(idField(), renamed);
        assertTrue(converge(List.of(customer()), fields, List.of()));

        assertColumnExists("customer", "account_number");
        assertColumnGone("customer", "acct_no");
        assertEquals("carried", jdbcTemplate.queryForObject(
                "SELECT account_number FROM customer WHERE id = 1", String.class));
    }

    @Test
    void halfAppliedColumnRename_bothColumnsPresent_failsFast() {
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, "
                + "acct_no VARCHAR(100), account_number VARCHAR(100))");

        SysField renamed = fieldWithColumn("accountNumber", "account_number", FieldType.STRING, 100, false);
        renamed.setRenamedFrom("acctNo");
        List<SysField> fields = List.of(idField(), renamed);

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> converge(List.of(customer()), fields, List.of()));
        assertTrue(e.getMessage().contains("acct_no"),
                "the error must name the conflicting columns: " + e.getMessage());
    }

    // ---- table identity -------------------------------------------------------

    @Test
    void missingTable_isCreatedFromCodeDefinition_withIndexes() throws Exception {
        List<SysField> fields = List.of(idField(),
                field("email", FieldType.STRING, 64, false));
        List<SysModelIndex> indexes = List.of(idx("uk_customer_email", List.of("email"), true));

        assertTrue(converge(List.of(customer()), fields, indexes), "genesis must execute DDL");
        assertColumnExists("customer", "email");
        assertIndexExists("customer", "uk_customer_email");

        assertFalse(converge(List.of(customer()), fields, indexes), "second run must plan nothing");
    }

    @Test
    void preExistingTable_behindModelAddedDiff_isAdoptedNotRecreated() throws Exception {
        // The model's sys_* rows are gone (diff says the model is added) but the physical
        // table survived with a partial shape plus an orphan: convergence adopts it —
        // missing declared columns/indexes are added, the orphan drops, no CREATE races.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, orphan VARCHAR(8))");
        jdbcTemplate.execute("INSERT INTO customer (id, orphan) VALUES (7, 'x')");

        SysModel customer = customer();
        List<SysField> fields = List.of(idField(), field("email", FieldType.STRING, 64, false));
        List<SysModelIndex> indexes = List.of(idx("idx_customer_email", List.of("email"), false));
        SchemaDiff diff = new SchemaDiff(
                new EntityDiff<>(List.of(customer), List.of(), List.of()),
                new EntityDiff<>(fields, List.of(), List.of()),
                EntityDiff.<SysOptionSet>empty(),
                EntityDiff.<SysOptionItem>empty(),
                new EntityDiff<>(indexes, List.of(), List.of()));
        assertTrue(converge(List.of(customer), fields, indexes, diff));

        assertColumnExists("customer", "email");
        assertColumnGone("customer", "orphan");
        assertIndexExists("customer", "idx_customer_email");
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class),
                "adoption must keep the existing rows");
    }

    @Test
    void declaredModelRename_renamesTableAndKeepsData() throws Exception {
        jdbcTemplate.execute("CREATE TABLE old_customer (id BIGINT NOT NULL PRIMARY KEY)");
        jdbcTemplate.execute("INSERT INTO old_customer (id) VALUES (1)");

        SysModel before = customer();
        before.setModelName("OldCustomer");
        before.setTableName("old_customer");
        SysModel after = customer();
        List<SysField> fields = List.of(idField());

        // The snapshot must cover the prior table too — the scanner widens its snapshot
        // universe with the diff's fromDb tables and renamedFrom derivations for exactly
        // this reason (MetadataAnnotationScanner#withPriorTables).
        ReferenceColumnResolver.stampSysFields(fields);
        PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, List.of(before, after));
        assertTrue(orchestrator.converge(List.of(after), fields, List.of(),
                diffWithModifiedModel(after, before, SchemaDiff.Kind.RENAME), facts));

        assertTableExists("customer");
        assertTableGone("old_customer");
        assertEquals(1, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM customer", Integer.class),
                "declared model rename must preserve table data");
    }

    @Test
    void undeclaredTableRetarget_oldTableStillExists_failsFast() throws Exception {
        // A bare tableName change while the old table physically exists: creating the new
        // table would silently divorce the data — the convergence lane refuses to guess.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY)");

        SysModel before = customer();                    // tableName "customer"
        SysModel after = customer();
        after.setTableName("customer_v2");
        List<SysField> fields = List.of(idField());
        ReferenceColumnResolver.stampSysFields(fields);
        PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, List.of(before, after));

        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> orchestrator.converge(List.of(after), fields, List.of(),
                        diffWithModifiedModel(after, before, SchemaDiff.Kind.MODIFY), facts));
        assertTrue(e.getMessage().contains("customer_v2"), e.getMessage());
        assertTableGone("customer_v2");

        // After the human renames manually, the same boot input converges cleanly.
        jdbcTemplate.execute("ALTER TABLE customer RENAME TO customer_v2");
        PhysicalSchema healed = PhysicalSchemaReader.readManagedTables(dataSource, List.of(before, after));
        assertDoesNotThrow(() -> orchestrator.converge(List.of(after), fields, List.of(),
                diffWithModifiedModel(after, before, SchemaDiff.Kind.MODIFY), healed));
    }

    // ---- ownership boundaries ---------------------------------------------

    @Test
    void projectionModel_isNeverConverged() throws Exception {
        // A projection declares a subset of the owner's table; the owner's other columns
        // must not read as undeclared drift when only the projection is in scope.
        jdbcTemplate.execute("CREATE TABLE customer (id BIGINT NOT NULL PRIMARY KEY, "
                + "email VARCHAR(64), owner_only VARCHAR(64))");

        SysModel report = customer();
        report.setModelName("CustomerReport");
        report.setProjection(Boolean.TRUE);
        SysField reportId = idField();
        reportId.setModelName("CustomerReport");

        assertFalse(converge(List.of(report), List.of(reportId), List.of()),
                "a projection plans no DDL at all");
        assertColumnExists("customer", "owner_only");
    }

    @Test
    void nonRdbmsModel_isSkipped() throws Exception {
        SysModel esModel = customer();
        esModel.setModelName("SearchDoc");
        esModel.setTableName("search_doc");
        esModel.setStorageType(StorageType.ES);
        SysField esId = idField();
        esId.setModelName("SearchDoc");

        assertFalse(converge(List.of(esModel), List.of(esId), List.of()),
                "a non-RDBMS model has no physical table to converge");
        assertTableGone("search_doc");
    }

    // ---- helpers --------------------------------------------------------

    private void assertTableExists(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                Integer.class, table);
        assertNotNull(count);
        assertTrue(count >= 1, "table " + table + " should exist");
    }

    private void assertTableGone(String table) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM INFORMATION_SCHEMA.TABLES WHERE LOWER(TABLE_NAME) = LOWER(?)",
                Integer.class, table);
        assertEquals(0, count, "table " + table + " must NOT exist");
    }

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

    /**
     * Match by exact name OR by H2's UNIQUE-index synthetic suffix
     * (e.g. {@code uk_customer_email_INDEX_2}), mirroring {@code IndexNameCompat}.
     */
    private void assertIndexExists(String table, String indexName) {
        assertTrue(indexCount(table, indexName) >= 1,
                "index " + table + "." + indexName + " should exist; existing: "
                        + jdbcTemplate.queryForList(
                                "SELECT INDEX_NAME FROM INFORMATION_SCHEMA.INDEXES "
                                        + "WHERE LOWER(TABLE_NAME) = LOWER(?)",
                                String.class, table));
    }

    private void assertIndexGone(String table, String indexName) {
        assertEquals(0, indexCount(table, indexName),
                "index " + table + "." + indexName + " must NOT exist");
    }

    private Integer indexCount(String table, String indexName) {
        return jdbcTemplate.queryForObject(
                "SELECT COUNT(DISTINCT INDEX_NAME) FROM INFORMATION_SCHEMA.INDEXES "
                        + "WHERE LOWER(TABLE_NAME) = LOWER(?) "
                        + "AND (LOWER(INDEX_NAME) = LOWER(?) OR LOWER(INDEX_NAME) LIKE LOWER(?))",
                Integer.class, table, indexName, indexName + "_index_%");
    }
}
