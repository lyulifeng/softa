package io.softa.starter.metadata.ddl.dialect;

import org.junit.jupiter.api.Test;

import io.softa.starter.metadata.ddl.context.FieldDdlCtx;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Which storage type a declared {@code @Field(length)} lands on, per dialect.
 *
 * <p>The tiers are the only thing standing between a model author and a destructive MODIFY: a column
 * holding rendered contract HTML is MEDIUMTEXT in the database, and if the annotation lane could not
 * express that, moving its model to annotations would emit a shrink that MySQL refuses once a single
 * row outgrows the smaller type. Pinned here because the boundaries are easy to "simplify" away.
 */
class StringLengthTierTest {

    private final MySqlDdlDialect mysql = new MySqlDdlDialect(BuiltinDdlMetadataResolver.INSTANCE);
    private final PostgreSqlDdlDialect postgres = new PostgreSqlDdlDialect(BuiltinDdlMetadataResolver.INSTANCE);

    private static FieldDdlCtx varchar(int length) {
        FieldDdlCtx field = new FieldDdlCtx();
        field.setDbType("VARCHAR");
        field.setLength(length);
        return field;
    }

    @Test
    void mysqlKeepsAVarcharWithinTheUtf8mb4RowLimit() {
        assertThat(mysql.buildTypeDeclaration(varchar(64))).isEqualTo("VARCHAR(64)");
        assertThat(mysql.buildTypeDeclaration(varchar(16383))).isEqualTo("VARCHAR(16383)");
    }

    @Test
    void mysqlRendersTextPastTheVarcharLimit() {
        // The established convention for a "big string" — SysField.expression and friends.
        assertThat(mysql.buildTypeDeclaration(varchar(20000))).isEqualTo("TEXT");
        assertThat(mysql.buildTypeDeclaration(varchar(65535))).isEqualTo("TEXT");
    }

    @Test
    void mysqlRendersMediumtextPastTextsOwnCapacity() {
        assertThat(mysql.buildTypeDeclaration(varchar(65536))).isEqualTo("MEDIUMTEXT");
        // EmpDocument.htmlBody — matches the column that already exists.
        assertThat(mysql.buildTypeDeclaration(varchar(16_777_215))).isEqualTo("MEDIUMTEXT");
    }

    @Test
    void mysqlSwitchesTheDbTypeSoVarcharOnlyTemplateGuardsStopApplying() {
        FieldDdlCtx text = varchar(20000);
        mysql.buildTypeDeclaration(text);
        assertThat(text.getDbType()).isEqualTo("TEXT");

        FieldDdlCtx medium = varchar(16_777_215);
        mysql.buildTypeDeclaration(medium);
        assertThat(medium.getDbType()).isEqualTo("MEDIUMTEXT");
    }

    @Test
    void postgresDeclaresTheLengthUntilItsOwnVarcharCap() {
        assertThat(postgres.buildTypeDeclaration(varchar(20000))).isEqualTo("VARCHAR(20000)");
        assertThat(postgres.buildTypeDeclaration(varchar(10_485_760))).isEqualTo("VARCHAR(10485760)");
    }

    @Test
    void postgresRendersTextPastIt() {
        // Same declaration as the MEDIUMTEXT case above: a model author picks one length and both
        // dialects have to produce something legal.
        assertThat(postgres.buildTypeDeclaration(varchar(16_777_215))).isEqualTo("TEXT");
    }
}
