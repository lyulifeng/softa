package io.softa.framework.orm.jdbc.database;

import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.exception.ConfigurationException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.datasource.DataSourceConfig;
import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.jdbc.database.dialect.*;

/**
 * Database utility class
 */
@Component
public class DBUtil {

    @Value("${spring.datasource.dynamic.enable:false}")
    private boolean enableMultiDataSource;

    @Value("${spring.datasource.url:}")
    private String singleDbUrl;

    /**
     * Name of the nondeterministic ICU collation stamped onto PostgreSQL string columns, or
     * blank when the columns are case-sensitive. Only its emptiness is read here — the name
     * itself is the DDL generator's business; what this class needs to know is whether pattern
     * matching has to fall back from {@code ILIKE} (rejected on such columns) to {@code LIKE}.
     */
    @Value("${system.metadata.ddl.postgres-string-collation:}")
    private String pgStringCollation;

    private static DialectInterface singleDbDialect;

    /**
     * Static mirror of {@link #pgStringCollation}'s emptiness, so the static
     * {@link #createDialect(DatabaseType)} can see it. Set in {@link #initDBType()} before any
     * dialect is built on the single-datasource path.
     *
     * <p>Multi-datasource caveat: {@code DataSourceConfig} builds its dialects through the same
     * factory, but if it initializes before this bean it sees the default ({@code false}, i.e.
     * {@code ILIKE}). Case-insensitive collation is currently only wired for the single
     * datasource; a dynamic-datasource deployment would need the flag per data source anyway,
     * since two data sources can disagree about it.
     */
    private static volatile boolean pgCaseInsensitiveCollation;

    /**
     * Initialize dialect constant of single data source
     */
    @PostConstruct
    public void initDBType() {
        pgCaseInsensitiveCollation = pgStringCollation != null && !pgStringCollation.isBlank();
        if (!enableMultiDataSource) {
            // Parse the database type from the JDBC URL, as the single datasource
            DatabaseType databaseType = parseDatabaseType(singleDbUrl);
            singleDbDialect = createDialect(databaseType);
        }
    }

    /**
     * Parse the database type from the JDBC URL
     * @param jdbcUrl JDBC URL
     * @return Database type
     */
    public static DatabaseType parseDatabaseType(String jdbcUrl) {
        if (jdbcUrl != null && jdbcUrl.startsWith("jdbc:")) {
            String withoutJdbc = jdbcUrl.substring(5);
            int colonIndex = withoutJdbc.indexOf(":");
            if (colonIndex > 0) {
                String dbType = withoutJdbc.substring(0, colonIndex);
                DatabaseType databaseType = DatabaseType.valueOf(dbType.toUpperCase());
                Assert.notNull(databaseType, "The database type of {0} is not supported!", jdbcUrl);
                return databaseType;
            }
        }
        throw new ConfigurationException("The datasource url {0} is not invalid!", jdbcUrl);
    }

    /**
     * Create the database dialect by the database type
     * @param databaseType Database type
     * @return Database dialect
     */
    public static DialectInterface createDialect(DatabaseType databaseType) {
        if (DatabaseType.MYSQL.equals(databaseType)) {
            return new MySQLDialect();
        } else if (DatabaseType.POSTGRESQL.equals(databaseType)) {
            return new PostgreSQLDialect(pgCaseInsensitiveCollation);
        } else if (DatabaseType.ORACLE.equals(databaseType)) {
            return new OracleDialect();
        } else if (DatabaseType.SQLSERVER.equals(databaseType)) {
            return new SQLServerDialect();
        } else {
            throw new ConfigurationException("Dialects of database {0} are not currently supported!", databaseType.getType());
        }
    }

    /**
     * Get the current database dialect.
     * @return Database dialect
     */
    public static DialectInterface getDbDialect() {
        if (singleDbDialect != null) {
            return singleDbDialect;
        } else {
            // Get the current database dialect in multi-datasource scenario
            return DataSourceConfig.getCurrentDialect();
        }
    }

    /**
     * Convert Operator to SQL query condition predicate
     * @param operator Operator
     * @return SQL query condition predicate
     */
    public static String getPredicate(Operator operator) {
        return getDbDialect().getPredicate(operator);
    }

    /**
     * Get the paging clause according to the page number and page size.
     * Calculate the offset based on the page number and page size.
     *
     * @param pageSize Page size is controlled between [0, MAX_BATCH_SIZE]
     * @param pageNumber Page number starts from 1, corresponding to the offset starting from 0
     * @return Paging SQL clause
     */
    public static StringBuilder getDialectPageClause(int pageSize, int pageNumber) {
        int size = pageSize < 1 ? 1 : Math.min(pageSize, BaseConstant.MAX_BATCH_SIZE);
        int offset = pageNumber <= 1 ? 0 : Math.max((pageNumber - 1) * size, 0);
        return getDbDialect().getPageClause(size, offset);
    }

}
