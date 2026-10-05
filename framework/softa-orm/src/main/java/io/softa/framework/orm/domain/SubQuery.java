package io.softa.framework.orm.domain;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Sub query conditions, used to specify the fields, filters, orders, pageNumber, pageSize of the sub query.
 * Application scenarios of different field types:
 *      1. ManyToOne, OneToOne: specify the field list to get.
 *      2. OneToMany, ManyToMany: specify the fields to get, filters, orders, aggFunctions, topN.
 *      3. Count query (+ filters): specify the `count = true` to get the count for every group.
 *      For example, get the count of each department's employees.
 *      Note:
 *      If `filters` is specified, the `count` query will be based on the `filters` conditions.
 *      4. TopN query on OneToMany field: specify the `topN` and `orders` parameters to get the top N data.
 *      For example, set the topN = 10 and orders = ["createTime", "DESC"].
 *      Note:
 *      The topN query is not supported by all databases,
 *      only databases that support the `ROW_NUMBER()` function and `OVER` clause.
 *      Some databases support the `topN` query, such as
 *          Oracle Database: 10g and later
 * 	        Microsoft SQL Server: 2005 and later
 * 	        PostgreSQL: 8.4 and later
 * 	        MySQL: 8.0 and later
 * 	        IBM DB2: 8.1 and later
 * 	        SQLite: 3.25.0 and later
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class SubQuery implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Sub query fields list.", example = "[\"id\", \"name\"]")
    private List<String> fields;

    private Filters filters;

    private Orders orders;

    @Schema(description = "Only return the count of related records, true/false")
    private Boolean count;

    @Schema(description = "TopN query on OneToMany field, using with `orders`.", example = "3")
    private Integer topN;

    @Schema(description = "Sub queries for relational fields: {fieldName: SubQuery}", example = "{}")
    private Map<String, SubQuery> subQueries;

    /**
     * OneToMany onto a timeline model: return every slice of the related rows, not only the one in
     * effect today.
     *
     * <p>A related timeline model is read like any other, so the expansion is clamped to today the
     * same way a top-level read is — and a child table showing the versions of something then shows
     * only the current one. A version that starts next month, the very thing such a table is opened
     * to check, is silently absent. The top-level query already has this switch; a sub query had no
     * way to ask for it.
     *
     * <p>Opt-in rather than inferred from the related model being a timeline: a child list that wants
     * the current state of each related entity is just as legitimate, and existing readers rely on
     * that being the default.
     *
     * <p>One limit, shared with every across-timeline read: on the child rows, a to-one field onto
     * ANOTHER timeline model is returned as a display reference only, and a nested sub query for it
     * is not expanded. That is deliberate. Its display name comes from a join aligned with each
     * child slice's dates, so a version from last year shows the name that applied last year;
     * expanding by id would fetch today's version for every slice and show history with today's
     * names. A to-one onto a model that is not a timeline expands as usual.
     */
    @Schema(description = "OneToMany onto a timeline model: return all slices instead of the one in effect today.")
    private Boolean acrossTimeline;

    public SubQuery(List<String> fields) {
        this.fields = fields;
    }

}
