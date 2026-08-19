package io.softa.starter.metadata.ddl;

import java.util.List;
import org.h2.jdbcx.JdbcDataSource;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import io.softa.starter.metadata.catalog.SysCatalog;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchema;
import io.softa.starter.metadata.ddl.introspect.PhysicalSchemaReader;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.scanner.SysJdbcLoader;
import io.softa.starter.metadata.scanner.annotation.AnnotationParser;
import io.softa.starter.metadata.scanner.annotation.AnnotationScanResult;

import static org.junit.jupiter.api.Assertions.*;

/**
 * The golden invariant behind the catalog bootstrap: on a completely empty database,
 * parsing the REAL catalog entities ({@link SysCatalog#BOOT_READ_ENTITIES}) and running
 * {@link DdlOrchestrator#reconcilePhysical} must leave the schema in a state where
 * {@link SysJdbcLoader}'s strict read succeeds — i.e. the loader's SELECT set is ⊆ the
 * physical column set, with no baseline SQL and no migration involved.
 *
 * <p>This is the end-to-end proof for the two problems the bootstrap exists to solve:
 * a fresh database self-bootstraps (previously: boot failure until the baseline DDL was
 * loaded), and a catalog-column addition converges on an existing database (previously:
 * a hand-written migration before every such upgrade — the V34/V37 lesson).
 */
class CatalogBootstrapEndToEndTest {

    private JdbcTemplate jdbcTemplate;
    private JdbcDataSource dataSource;
    private DdlOrchestrator orchestrator;

    @BeforeEach
    void setUp() {
        dataSource = new JdbcDataSource();
        dataSource.setURL("jdbc:h2:mem:catboot_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1");
        dataSource.setUser("sa");
        dataSource.setPassword("");
        jdbcTemplate = new JdbcTemplate(dataSource);
        orchestrator = new DdlOrchestrator(
                jdbcTemplate, BuiltinDdlMetadataResolver.INSTANCE, "jdbc:mysql://localhost/test");
    }

    private AnnotationScanResult parseRealCatalogEntities() {
        AnnotationScanResult catalog = new AnnotationParser()
                .parse(SysCatalog.BOOT_READ_ENTITIES, List.of());
        ReferenceColumnResolver.stampSysFields(catalog.fields(), catalog.fields());
        return catalog;
    }

    private boolean reconcile(AnnotationScanResult catalog) throws Exception {
        PhysicalSchema facts = PhysicalSchemaReader.readManagedTables(dataSource, catalog.models());
        return orchestrator.reconcilePhysical(
                catalog.models(), catalog.fields(), catalog.modelIndexes(), facts);
    }

    @Test
    void freshDatabase_bootstrapsAllCatalogTables_andStrictReadSucceeds() throws Exception {
        AnnotationScanResult catalog = parseRealCatalogEntities();
        assertEquals(SysCatalog.BOOT_READ_ENTITIES.size(), catalog.models().size(),
                "every boot-read entity must parse into a model");

        assertTrue(reconcile(catalog), "genesis must execute DDL");

        // THE invariant: after the bootstrap, the strict read must succeed on the
        // freshly-created (empty) catalog — its SELECT covers every entity column.
        AnnotationScanResult fromDb = new SysJdbcLoader(jdbcTemplate).load();
        assertTrue(fromDb.models().isEmpty(), "a fresh catalog reads as genuinely empty");

        assertFalse(reconcile(catalog), "second boot must plan nothing (idempotent)");
    }

    @Test
    void catalogColumnAddedInCode_convergesOnExistingDatabase() throws Exception {
        AnnotationScanResult catalog = parseRealCatalogEntities();
        reconcile(catalog);

        // Simulate an environment created before a catalog-column addition (the V34/V37
        // shape): hand-drop one sys_model column, as an older baseline would lack it.
        jdbcTemplate.execute("ALTER TABLE sys_model DROP COLUMN multi_country");
        assertThrows(Exception.class, () -> new SysJdbcLoader(jdbcTemplate).load(),
                "precondition: the strict read must fail against the older schema");

        assertTrue(reconcile(catalog), "the reconcile must re-add the missing column");
        assertDoesNotThrow(() -> new SysJdbcLoader(jdbcTemplate).load(),
                "after the reconcile the strict read must succeed — no migration involved");
    }
}
