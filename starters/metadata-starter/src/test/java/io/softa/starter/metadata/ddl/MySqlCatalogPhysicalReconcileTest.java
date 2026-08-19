package io.softa.starter.metadata.ddl;

/** {@link DdlOrchestrator#reconcilePhysical} against H2 in MySQL mode, driving the MySQL dialect. */
class MySqlCatalogPhysicalReconcileTest extends AbstractCatalogPhysicalReconcileTest {

    @Override
    protected String h2JdbcUrl() {
        return "jdbc:h2:mem:catrec_mysql_" + System.nanoTime()
                + ";MODE=MySQL;DATABASE_TO_LOWER=TRUE;CASE_INSENSITIVE_IDENTIFIERS=TRUE;DB_CLOSE_DELAY=-1";
    }

    @Override
    protected String productionJdbcUrl() {
        return "jdbc:mysql://localhost/test";
    }
}
