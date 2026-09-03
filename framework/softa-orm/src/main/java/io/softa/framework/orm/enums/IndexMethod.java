package io.softa.framework.orm.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Physical shape of a declared {@code @Index} — a dialect-neutral intent that each
 * DDL dialect renders with its own best implementation. Business models never name
 * database-specific access methods or operator classes directly.
 *
 * <p>Rendering per dialect:
 * <table>
 *   <tr><th>Method</th><th>MySQL</th><th>PostgreSQL</th></tr>
 *   <tr><td>{@link #BTREE}</td><td>ordinary index</td><td>ordinary index</td></tr>
 *   <tr><td>{@link #SEARCH}</td><td>ordinary index (prefix-only help)</td>
 *       <td>{@code USING gin (col gin_trgm_ops)}</td></tr>
 *   <tr><td>{@link #PREFIX}</td><td>ordinary index</td>
 *       <td>{@code (col text_pattern_ops)}</td></tr>
 * </table>
 *
 * <p>The asymmetry is inherent, not an omission: under MySQL's case-insensitive
 * collations a plain B-tree already serves prefix matching, while {@code CONTAINS}
 * ({@code LIKE '%x%'}) is unindexable there; PostgreSQL needs the explicit operator
 * class for prefix scans and offers trigram GIN for substring search.
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Index Method")
public enum IndexMethod {

    /** Ordinary ordered index — equality, ranges, sorting. The default. */
    @OptionItem(label = "B-tree")
    BTREE("Btree"),

    /**
     * Substring / fuzzy search index for the {@code CONTAINS} and {@code START_WITH}
     * operators. PostgreSQL renders trigram GIN ({@code pg_trgm} is provisioned by the
     * DDL executor when first needed). Single string-typed field only; never unique.
     *
     * <p>Trigram matching needs three consecutive characters from the search term to
     * narrow the index — 1–2 character terms still answer correctly but without
     * index acceleration.
     */
    @OptionItem(label = "Fuzzy Search")
    SEARCH("Search"),

    /**
     * Byte-ordered prefix index for {@code START_WITH} / {@code CHILD_OF} on
     * machine-generated values (id paths, codes). PostgreSQL renders a B-tree with
     * {@code text_pattern_ops} so {@code LIKE 'abc%'} takes a range scan regardless
     * of the database collation. Single string-typed field only; never unique.
     */
    @OptionItem(label = "Prefix Match")
    PREFIX("Prefix");

    @JsonValue
    private final String type;
}
