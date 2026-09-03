package io.softa.framework.orm.jdbc.database.dialect;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.enums.Operator;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Pattern-operator mapping on PostgreSQL. {@code CHILD_OF} is a prefix match on
 * machine-generated id paths and must render plain {@code LIKE}: case-insensitivity
 * buys nothing on digit/slash paths, and only {@code LIKE} can take a
 * {@code text_pattern_ops} prefix index ({@code ILIKE} has no B-tree operator class,
 * forcing a full scan on every tree-scoped query). The human-text operators keep
 * {@code ILIKE} to reproduce the case-insensitive matching MySQL's collations give.
 */
class PostgreSQLDialectTest {

    private final PostgreSQLDialect dialect = new PostgreSQLDialect();

    @Test
    void childOfIsAPlainLikePrefixMatch() {
        assertEquals("LIKE", dialect.getPredicate(Operator.CHILD_OF));
    }

    @Test
    void humanTextPatternOperatorsStayCaseInsensitive() {
        assertEquals("ILIKE", dialect.getPredicate(Operator.CONTAINS));
        assertEquals("NOT ILIKE", dialect.getPredicate(Operator.NOT_CONTAINS));
        assertEquals("ILIKE", dialect.getPredicate(Operator.START_WITH));
        assertEquals("NOT ILIKE", dialect.getPredicate(Operator.NOT_START_WITH));
    }
}
