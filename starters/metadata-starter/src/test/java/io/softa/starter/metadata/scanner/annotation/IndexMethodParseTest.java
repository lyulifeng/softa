package io.softa.starter.metadata.scanner.annotation;

import java.util.List;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IndexMethod;
import io.softa.starter.metadata.entity.SysModelIndex;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * {@code @Index(method = ...)} parsing and its scan-time constraints.
 *
 * <p>BTREE persists as {@code null}: {@code sys_model_index} rows written before the
 * method column existed carry null, and the diff compares catalog fields verbatim —
 * emitting the default explicitly would mark every pre-existing index "updated" on
 * the first boot after an upgrade and rebuild all of them for nothing.
 *
 * <p>SEARCH / PREFIX render a per-column operator class, so they are constrained to
 * exactly one character-typed field and can never back a unique constraint; the
 * parser rejects violations by naming the model, instead of letting the database
 * fail the generated DDL.
 */
class IndexMethodParseTest {

    private final AnnotationParser parser = new AnnotationParser();

    // ------- fixtures ----------------------------------------------------

    @Model(label = "Article", multiTenant = false)
    @Index(fields = {"title"}, method = IndexMethod.SEARCH)
    @Index(fields = {"path"}, method = IndexMethod.PREFIX)
    @Index(fields = {"code"}, unique = true)
    @SuppressWarnings("unused")
    static class Article extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Field(length = 200)
        private String title;
        @Field(length = 500)
        private String path;
        @Field(length = 64)
        private String code;
    }

    @Model(label = "Bad Unique", multiTenant = false)
    @Index(fields = {"name"}, unique = true, method = IndexMethod.SEARCH)
    @SuppressWarnings("unused")
    static class BadUnique extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Field(length = 64)
        private String name;
    }

    @Model(label = "Bad Composite", multiTenant = false)
    @Index(fields = {"name", "code"}, method = IndexMethod.PREFIX)
    @SuppressWarnings("unused")
    static class BadComposite extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Field(length = 64)
        private String name;
        @Field(length = 64)
        private String code;
    }

    @Model(label = "Bad Field Type", multiTenant = false)
    @Index(fields = {"amount"}, method = IndexMethod.SEARCH)
    @SuppressWarnings("unused")
    static class BadFieldType extends AuditableModel {
        @Field
        private Long id;

        @Override
        public Long getId() {
            return id;
        }

        @Field(fieldType = FieldType.INTEGER)
        private Integer amount;
    }

    // ------- cases -------------------------------------------------------

    @Test
    void parsesMethodAndStoresBtreeAsNull() {
        List<SysModelIndex> indexes =
                parser.parse(List.of(Article.class), List.of()).modelIndexes();
        assertEquals(3, indexes.size());
        assertEquals(IndexMethod.SEARCH, byName(indexes, "idx_article_title").getMethod());
        assertEquals(IndexMethod.PREFIX, byName(indexes, "idx_article_path").getMethod());
        assertNull(byName(indexes, "uk_article_code").getMethod());
    }

    @Test
    void rejectsUniqueWithNonBtreeMethod() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(BadUnique.class), List.of()));
        assertTrue(e.getMessage().contains("unique"), e.getMessage());
    }

    @Test
    void rejectsCompositeNonBtreeIndex() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(BadComposite.class), List.of()));
        assertTrue(e.getMessage().contains("exactly one"), e.getMessage());
    }

    @Test
    void rejectsNonStringFieldForPatternMethods() {
        IllegalStateException e = assertThrows(IllegalStateException.class,
                () -> parser.parse(List.of(BadFieldType.class), List.of()));
        assertTrue(e.getMessage().contains("INTEGER"), e.getMessage());
    }

    private static SysModelIndex byName(List<SysModelIndex> indexes, String name) {
        return indexes.stream()
                .filter(idx -> name.equals(idx.getIndexName()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("index " + name + " not parsed; got "
                        + indexes.stream().map(SysModelIndex::getIndexName).toList()));
    }
}
