package io.softa.starter.metadata.ddl;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.IndexMethod;
import io.softa.starter.metadata.ddl.context.ModelDdlCtx;
import io.softa.starter.metadata.ddl.dialect.MySqlDdlDialect;
import io.softa.starter.metadata.ddl.dialect.PostgreSqlDdlDialect;
import io.softa.starter.metadata.ddl.spi.BuiltinDdlMetadataResolver;
import io.softa.starter.metadata.entity.SysField;
import io.softa.starter.metadata.entity.SysModel;
import io.softa.starter.metadata.entity.SysModelIndex;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code IndexMethod} rendering through the real DDL templates, driven by contexts
 * built the production way ({@link SysDdlContextBuilder}).
 *
 * <p>PostgreSQL is where the intent becomes physical: SEARCH renders trigram GIN,
 * PREFIX renders a {@code text_pattern_ops} B-tree. MySQL renders every method as a
 * plain index — its collations already serve prefix matching, and it has no substring
 * index to offer — so the same model must stay renderable there unchanged.
 */
class IndexMethodRenderTest {

    private final PostgreSqlDdlDialect postgres =
            new PostgreSqlDdlDialect(BuiltinDdlMetadataResolver.INSTANCE);
    private final MySqlDdlDialect mysql =
            new MySqlDdlDialect(BuiltinDdlMetadataResolver.INSTANCE);

    @Test
    void postgresRendersSearchAsTrigramGin() {
        String sql = postgres.alterIndexDDL(indexChangeCtx(IndexMethod.SEARCH)).toString();
        assertTrue(sql.contains("CREATE INDEX idx_article_title ON article USING gin (title gin_trgm_ops);"), sql);
    }

    @Test
    void postgresRendersPrefixAsPatternOpsBtree() {
        String sql = postgres.alterIndexDDL(indexChangeCtx(IndexMethod.PREFIX)).toString();
        assertTrue(sql.contains("CREATE INDEX idx_article_title ON article (title text_pattern_ops);"), sql);
    }

    @Test
    void postgresRendersDefaultAsPlainIndex() {
        String sql = postgres.alterIndexDDL(indexChangeCtx(null)).toString();
        assertTrue(sql.contains("CREATE INDEX idx_article_title ON article (title);"), sql);
        assertFalse(sql.contains("USING gin"), sql);
    }

    @Test
    void postgresCreateTableRendersMethodOnInlineIndexes() {
        String sql = postgres.createTableDDL(createCtx(IndexMethod.SEARCH)).toString();
        assertTrue(sql.contains("USING gin (title gin_trgm_ops)"), sql);
    }

    @Test
    void mysqlIgnoresMethodAndRendersPlainIndexes() {
        String alterSql = mysql.alterIndexDDL(indexChangeCtx(IndexMethod.SEARCH)).toString();
        String createSql = mysql.createTableDDL(createCtx(IndexMethod.PREFIX)).toString();
        for (String sql : List.of(alterSql, createSql)) {
            assertFalse(sql.contains("gin_trgm_ops"), sql);
            assertFalse(sql.contains("text_pattern_ops"), sql);
            assertTrue(sql.contains("idx_article_title"), sql);
        }
    }

    // ------- production-path context builders -----------------------------

    private static ModelDdlCtx createCtx(IndexMethod method) {
        return SysDdlContextBuilder.forCreate(model(), List.of(titleField()), List.of(index(method)));
    }

    private static ModelDdlCtx indexChangeCtx(IndexMethod method) {
        return SysDdlContextBuilder.forIndexChanges(model(), Map.of("title", "title"),
                List.of(index(method)), List.of(), List.of());
    }

    private static SysModel model() {
        SysModel model = new SysModel();
        model.setModelName("Article");
        model.setLabel("Article");
        model.setIdStrategy(IdStrategy.DISTRIBUTED_LONG);
        return model;
    }

    private static SysField titleField() {
        SysField field = new SysField();
        field.setModelName("Article");
        field.setFieldName("title");
        field.setLabel("Title");
        field.setFieldType(FieldType.STRING);
        field.setLength(200);
        return field;
    }

    private static SysModelIndex index(IndexMethod method) {
        SysModelIndex idx = new SysModelIndex();
        idx.setModelName("Article");
        idx.setIndexName("idx_article_title");
        idx.setIndexFields(List.of("title"));
        idx.setUniqueIndex(false);
        idx.setMethod(method);
        return idx;
    }
}
