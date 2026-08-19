package io.softa.starter.metadata.ddl;

/** {@link DdlOrchestrator#reconcilePhysical} against H2 in PostgreSQL mode, driving the PostgreSQL dialect. */
class PostgreSqlCatalogPhysicalReconcileTest extends AbstractCatalogPhysicalReconcileTest {

    @Override
    protected String h2JdbcUrl() {
        return "jdbc:h2:mem:catrec_pg_" + System.nanoTime()
                + ";MODE=PostgreSQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
    }

    @Override
    protected String productionJdbcUrl() {
        return "jdbc:postgresql://localhost/test";
    }
}
