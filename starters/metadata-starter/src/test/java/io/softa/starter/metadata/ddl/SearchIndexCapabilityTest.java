package io.softa.starter.metadata.ddl;

import java.util.ArrayList;
import java.util.List;
import javax.sql.DataSource;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.IndexMethod;
import io.softa.framework.orm.enums.StorageType;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchemaReader;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;

import static org.junit.jupiter.api.Assertions.*;

/**
 * End-to-end contract of the SEARCH-index capability guard, driven through the real
 * convergence lane against H2.
 *
 * <p><b>PostgreSQL posture</b> (H2 in PG mode — {@code pg_trgm} is genuinely absent there, the
 * same answer a locked-down managed database gives): the boot must SURVIVE, create the table,
 * and skip the trigram index — both when the index arrives with a brand-new table (the CREATE
 * TABLE templates render indexes inline, so a guard on the ALTER path alone would let a fresh
 * bootstrap emit {@code gin_trgm_ops} DDL that fails the boot) and when it is added to an
 * existing table. Non-SEARCH indexes planned in the same pass must still be created — the skip
 * is per index, never per model.
 *
 * <p><b>MySQL posture</b>: SEARCH renders as a plain index (the dialect has no substring index
 * to offer), so it must be physically created, not skipped — and certainly not warned about
 * with PostgreSQL remediation text.
 */
class SearchIndexCapabilityTest {

    // ---- PostgreSQL posture ----------------------------------------------

    @Test
    void freshBootstrapOnPostgresWithoutPgTrgm_createsTableAndSkipsSearchIndex() throws Exception {
        Env env = Env.postgres();

        boolean executed = env.converge();

        assertTrue(executed, "genesis CREATE TABLE must run");
        assertTrue(env.tableExists(), "the table itself must be created");
        assertFalse(env.indexExists("idx_customer_name_search"),
                "the trigram index cannot be built without pg_trgm and must be skipped");
        assertTrue(env.indexExists("uk_customer_code"),
                "the skip is per index — the unique index in the same plan must be created");
    }

    @Test
    void searchIndexAddedToAnExistingTableOnPostgres_isSkippedNotFatal() throws Exception {
        Env env = Env.postgres();
        env.indexes.clear();
        assertTrue(env.converge(), "bootstrap without the search index");

        env.indexes.add(env.searchIndex());
        // No exception = the boot survives; the index simply is not there.
        env.converge();
        assertFalse(env.indexExists("idx_customer_name_search"));
    }

    // ---- MySQL posture ----------------------------------------------------

    @Test
    void searchIndexOnMysql_isCreatedAsAPlainIndex() throws Exception {
        Env env = Env.mysql();

        assertTrue(env.converge());

        assertTrue(env.tableExists());
        assertTrue(env.indexExists("idx_customer_name_search"),
                "MySQL renders SEARCH as a plain index — it must be created, not skipped");
    }

    // ---- fixture -----------------------------------------------------------

    private static final class Env {
        final JdbcTemplate jdbcTemplate;
        final DataSource dataSource;
        final DdlOrchestrator orchestrator;
        final List<SysModelIndex> indexes = new ArrayList<>();

        private Env(String h2Url, String productionUrl) {
            JdbcDataSource ds = new JdbcDataSource();
            ds.setURL(h2Url);
            ds.setUser("sa");
            ds.setPassword("");
            this.dataSource = ds;
            this.jdbcTemplate = new JdbcTemplate(ds);
            this.orchestrator = new DdlOrchestrator(
                    jdbcTemplate, BuiltinDdlMetadataResolver.INSTANCE, productionUrl);
            indexes.add(searchIndex());
            indexes.add(uniqueIndex());
        }

        static Env postgres() {
            return new Env("jdbc:h2:mem:trgm_pg_" + System.nanoTime()
                    + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                    "jdbc:postgresql://localhost/test");
        }

        static Env mysql() {
            return new Env("jdbc:h2:mem:trgm_my_" + System.nanoTime()
                    + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1",
                    "jdbc:mysql://localhost/test");
        }

        boolean converge() throws Exception {
            List<SysModel> models = List.of(model());
            List<SysField> fields = fields();
            ReferenceColumnResolver.stampSysFields(fields);
            PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, models);
            return orchestrator.converge(models, fields, indexes,
                    io.softa.starter.metadata.scanner.diff.SchemaDiff.empty(), facts);
        }

        boolean tableExists() {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.tables "
                    + "WHERE LOWER(table_name) = 'customer'", Integer.class) > 0;
        }

        boolean indexExists(String indexName) {
            return jdbcTemplate.queryForObject("SELECT COUNT(*) FROM information_schema.indexes "
                    + "WHERE LOWER(index_name) = ?", Integer.class, indexName.toLowerCase()) > 0;
        }

        SysModelIndex searchIndex() {
            SysModelIndex i = new SysModelIndex();
            i.setModelName("Customer");
            i.setIndexName("idx_customer_name_search");
            i.setIndexFields(new ArrayList<>(List.of("name")));
            i.setUniqueIndex(false);
            i.setMethod(IndexMethod.SEARCH);
            return i;
        }

        private static SysModelIndex uniqueIndex() {
            SysModelIndex i = new SysModelIndex();
            i.setModelName("Customer");
            i.setIndexName("uk_customer_code");
            i.setIndexFields(new ArrayList<>(List.of("code")));
            i.setUniqueIndex(true);
            return i;
        }

        private static SysModel model() {
            SysModel m = new SysModel();
            m.setModelName("Customer");
            m.setLabel("Customer");
            m.setTableName("customer");
            m.setIdStrategy(IdStrategy.DISTRIBUTED_LONG);
            m.setStorageType(StorageType.RDBMS);
            m.setMultiTenant(false);
            return m;
        }

        private static List<SysField> fields() {
            List<SysField> fields = new ArrayList<>();
            fields.add(field("id", FieldType.LONG, null, true));
            fields.add(field("name", FieldType.STRING, 100, false));
            fields.add(field("code", FieldType.STRING, 64, false));
            return fields;
        }

        private static SysField field(String name, FieldType type, Integer length, boolean required) {
            SysField f = new SysField();
            f.setModelName("Customer");
            f.setFieldName(name);
            f.setColumnName(name);
            f.setLabel(name);
            f.setFieldType(type);
            if (length != null) {
                f.setLength(length);
            }
            f.setRequired(required);
            f.setDescription(name);
            return f;
        }
    }
}
