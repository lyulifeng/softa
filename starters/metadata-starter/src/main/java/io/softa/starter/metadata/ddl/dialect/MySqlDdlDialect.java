package io.softa.starter.metadata.ddl.dialect;

import lombok.extern.slf4j.Slf4j;

import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.metadata.ddl.context.FieldDdlCtx;
import io.softa.starter.metadata.ddl.mapping.MySqlDataType;
import io.softa.starter.metadata.ddl.spi.DdlMetadataResolver;

/**
 * MySQL DDL dialect using Pebble templates.
 *
 * <p>Depends on {@link DdlMetadataResolver}.
 */
@Slf4j
public class MySqlDdlDialect extends AbstractTemplateDdlDialect {

    private static final String TEMPLATE_DIR = "templates/sql/mysql/";

    /**
     * Max VARCHAR length in characters under utf8mb4 (65535-byte row limit /
     * 4 bytes per char). A declared {@code @Field(length)} above this cannot
     * be a VARCHAR — render TEXT instead (legacy escape hatch, e.g. a leftover
     * {@code length = 20000} declaration; new code declares
     * {@code fieldType = FieldType.TEXT} which renders MEDIUMTEXT).
     */
    private static final int MAX_VARCHAR_LENGTH = 16383;

    public MySqlDdlDialect(DdlMetadataResolver metadataResolver) {
        super(metadataResolver);
    }

    @Override
    protected String buildTypeDeclaration(FieldDdlCtx field) {
        if ("VARCHAR".equals(field.getDbType())
                && field.getLength() != null && field.getLength() > MAX_VARCHAR_LENGTH) {
            // Legacy escape hatch: the rendered TEXT column caps at 64KB BYTES
            // while the app-level check counts CHARACTERS — a utf8mb4 value can
            // pass validation yet fail to store. New code should declare
            // @Field(fieldType = FieldType.TEXT) instead (16MB MEDIUMTEXT).
            log.warn("Column {} declares STRING length {} > {}; rendering TEXT (64KB bytes). "
                            + "Prefer @Field(fieldType = FieldType.TEXT) which renders MEDIUMTEXT.",
                    field.getColumnName(), field.getLength(), MAX_VARCHAR_LENGTH);
            // Switch dbType too so template guards keyed on VARCHAR (e.g. the
            // required → DEFAULT '' clause, illegal on TEXT) don't apply.
            field.setDbType("TEXT");
            return "TEXT";
        }
        return super.buildTypeDeclaration(field);
    }

    @Override
    public DatabaseType getDatabaseType() {
        return DatabaseType.MYSQL;
    }

    @Override
    protected String getTemplateDir() {
        return TEMPLATE_DIR;
    }

    @Override
    protected String getDefaultDbType(FieldType fieldType) {
        return MySqlDataType.getDbType(fieldType);
    }
}
