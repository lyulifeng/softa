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
 *
 * <p><b>Optional case-insensitive collation.</b> When constructed with a collation name, string
 * columns are emitted as {@code VARCHAR(n) COLLATE "the_name"} — the migration path away from
 * MySQL's {@code utf8mb4_unicode_ci}, whose case-insensitivity PostgreSQL has no equivalent of
 * by default. The clause is appended to the <i>type declaration</i> and never to
 * {@code dbType}, which matters twice: {@link #buildTypeDeclaration} matches {@code dbType}
 * against {@link #LENGTH_TYPES} to decide whether to append {@code (n)} — a polluted value
 * silently loses the column width — and {@code AlterTable.peb} tests
 * {@code field.dbType == "VARCHAR"} to supply {@code DEFAULT ''} for a required string column.
 * Because all three templates interpolate {@code typeDeclaration}, one append covers CREATE
 * TABLE, ADD COLUMN and ALTER COLUMN TYPE; indexes inherit their column's collation, so
 * {@code AlterIndex.peb} needs nothing.
 */
public class PostgreSqlDdlDialect extends AbstractTemplateDdlDialect {

    private static final String TEMPLATE_DIR = "templates/sql/postgresql/";
    private static final Set<String> LENGTH_TYPES = Set.of("VARCHAR", "CHAR", "CHARACTER VARYING", "CHARACTER");
    private static final Set<String> NUMERIC_TYPES = Set.of("NUMERIC", "DECIMAL");

    /**
     * Field types whose column carries collatable business text, i.e. the ones a user filters,
     * sorts or uniquely indexes on.
     *
     * <p>{@code TEXT} / {@code JSON} / {@code DTO} are in deliberately. Once the ORM's pattern
     * operators fall back from {@code ILIKE} to {@code LIKE} (see {@code PostgreSQLDialect}),
     * case-insensitivity comes from the column collation alone, and
     * {@code DialectInterface.getPredicate} sees only the operator — never the field — so a
     * long-text column left uncollated would silently answer searches case-sensitively.
     *
     * <p>{@code FILTERS} / {@code ORDERS} / {@code MULTI_FILE} are out: they hold serialized
     * filter trees, sort specs and id CSVs that are never compared as text. A nondeterministic
     * collation costs a performance penalty and forfeits B-tree deduplication, so spending it
     * on a column nothing compares is pure loss.
     */
    private static final Set<FieldType> COLLATED_FIELD_TYPES = Set.of(
            FieldType.STRING, FieldType.OPTION, FieldType.MULTI_STRING, FieldType.MULTI_OPTION,
            FieldType.TEXT, FieldType.JSON, FieldType.DTO);

    /** Collatable rendered types, as a guard for a {@code dbType} overridden at the metadata layer. */
    private static final Set<String> COLLATABLE_DB_TYPES = Set.of(
            "VARCHAR", "CHAR", "CHARACTER VARYING", "CHARACTER", "TEXT");

    /** Blank when string columns stay case-sensitive (the default). */
    private final String stringCollation;

    public PostgreSqlDdlDialect(DdlMetadataResolver metadataResolver) {
        this(metadataResolver, null);
    }

    public PostgreSqlDdlDialect(DdlMetadataResolver metadataResolver, String stringCollation) {
        super(metadataResolver);
        this.stringCollation = stringCollation == null || stringCollation.isBlank()
                ? null : stringCollation.trim();
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
            declaration.append("(").append(field.getLength()).append(")");
        }
        if (needsCollation(field, normalized)) {
            declaration.append(" COLLATE \"").append(stringCollation).append("\"");
        }
        return declaration.toString();
    }

    /**
     * Whether this column should carry the configured collation. Both the logical field type and
     * the rendered column type have to be collatable — the second check keeps a {@code dbType}
     * overridden at the metadata layer (say a STRING rendered as {@code UUID} or {@code JSONB})
     * from getting a clause PostgreSQL would reject.
     */
    private boolean needsCollation(FieldDdlCtx field, String normalizedDbType) {
        return stringCollation != null
                && field.getFieldType() != null
                && COLLATED_FIELD_TYPES.contains(field.getFieldType())
                && COLLATABLE_DB_TYPES.contains(normalizedDbType);
    }
}
