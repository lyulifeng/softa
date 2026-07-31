package io.softa.starter.metadata.ddl.dialect;

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
public class MySqlDdlDialect extends AbstractTemplateDdlDialect {

    private static final String TEMPLATE_DIR = "templates/sql/mysql/";

    /**
     * Max VARCHAR length in characters under utf8mb4 (65535-byte row limit /
     * 4 bytes per char). A declared {@code @Field(length)} above this cannot
     * be a VARCHAR — render TEXT instead (e.g. {@code SysField.expression}
     * with {@code length = 20000} → {@code TEXT}).
     */
    private static final int MAX_VARCHAR_LENGTH = 16383;

    /**
     * TEXT's own capacity, in bytes. Above it the column has to be MEDIUMTEXT.
     *
     * <p>The tiers read the declared length as the capacity being asked for rather than as a strict
     * character count — the same looseness the TEXT tier already has, since a TEXT column carries no
     * declared length at all and {@code length = 20000} has never meant "20000 characters guaranteed".
     * What the number picks is the tier: up to 16383 a VARCHAR, past that TEXT, past 65535 MEDIUMTEXT.
     *
     * <p>The tier exists because the product already has such a column — {@code emp_document.html_body}
     * holds rendered contract HTML and was hand-written MEDIUMTEXT. Without a way to declare it, moving
     * that model to annotations would emit a MODIFY down to TEXT: a 256× cut that MySQL refuses outright
     * once one document exceeds 64 KB, and that quietly removes the headroom the column was given.
     */
    private static final int MAX_TEXT_LENGTH = 65535;

    public MySqlDdlDialect(DdlMetadataResolver metadataResolver) {
        super(metadataResolver);
    }

    @Override
    protected String buildTypeDeclaration(FieldDdlCtx field) {
        if ("VARCHAR".equals(field.getDbType()) && field.getLength() != null) {
            // Switch dbType too so template guards keyed on VARCHAR (e.g. the
            // required → DEFAULT '' clause, illegal on TEXT) don't apply.
            if (field.getLength() > MAX_TEXT_LENGTH) {
                field.setDbType("MEDIUMTEXT");
                return "MEDIUMTEXT";
            }
            if (field.getLength() > MAX_VARCHAR_LENGTH) {
                field.setDbType("TEXT");
                return "TEXT";
            }
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
