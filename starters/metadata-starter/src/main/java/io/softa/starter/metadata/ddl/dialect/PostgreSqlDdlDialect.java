package io.softa.starter.metadata.ddl.dialect;

import java.util.Set;

import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.ddl.context.FieldDdlCtx;
import io.softa.starter.metadata.ddl.mapping.PostgreSqlDataType;
import io.softa.starter.metadata.ddl.spi.DdlMetadataResolver;

/**
 * PostgreSQL DDL dialect using Pebble templates.
 *
 * <p>Depends on {@link DdlMetadataResolver}.
 */
public class PostgreSqlDdlDialect extends AbstractTemplateDdlDialect {

    private static final String TEMPLATE_DIR = "templates/sql/postgresql/";
    private static final Set<String> LENGTH_TYPES = Set.of("VARCHAR", "CHAR", "CHARACTER VARYING", "CHARACTER");
    private static final Set<String> NUMERIC_TYPES = Set.of("NUMERIC", "DECIMAL");

    /**
     * PostgreSQL's own cap on a declared {@code VARCHAR(n)}. Beyond it the declaration is rejected
     * outright, so a length that big renders as {@code TEXT} — unbounded here, and the same intent.
     *
     * <p>Only reachable through a length chosen for MySQL's MEDIUMTEXT tier (see
     * {@link MySqlDdlDialect}); without this guard the same {@code @Field} would build on MySQL and
     * fail at DDL time on PostgreSQL, which is not a difference a model author should have to know.
     */
    private static final int MAX_VARCHAR_LENGTH = 10_485_760;

    public PostgreSqlDdlDialect(DdlMetadataResolver metadataResolver) {
        super(metadataResolver);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.POSTGRESQL;
    }

    @Override
    protected String getTemplateDir() {
        return TEMPLATE_DIR;
    }

    @Override
    protected String getDefaultDbType(FieldType fieldType) {
        return PostgreSqlDataType.getDbType(fieldType);
    }

    @Override
    protected String buildTypeDeclaration(FieldDdlCtx field) {
        String dbType = field.getDbType();
        if (dbType == null) {
            return null;
        }
        String normalized = dbType.trim().toUpperCase();
        StringBuilder declaration = new StringBuilder(dbType);
        if (NUMERIC_TYPES.contains(normalized) && field.getLength() != null && field.getLength() > 0) {
            declaration.append("(").append(field.getLength());
            if (field.getScale() != null && field.getScale() > 0) {
                declaration.append(",").append(field.getScale());
            }
            declaration.append(")");
        } else if (LENGTH_TYPES.contains(normalized) && field.getLength() != null && field.getLength() > 0) {
            if (field.getLength() > MAX_VARCHAR_LENGTH) {
                field.setDbType("TEXT");
                return "TEXT";
            }
            declaration.append("(").append(field.getLength()).append(")");
        }
        return declaration.toString();
    }
}
