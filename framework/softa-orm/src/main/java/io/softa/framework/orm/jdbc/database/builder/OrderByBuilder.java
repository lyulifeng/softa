package io.softa.framework.orm.jdbc.database.builder;

import java.util.List;

import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.jdbc.database.SqlWrapper;
import io.softa.framework.orm.meta.ModelManager;

/**
 * OrderBy Builder
 * For paged queries:
 *      When `orders` in flexQuery is empty, using the `defaultOrder` configuration of model.
 *      When 'defaultOrder' is not configured, using the global default order `DEFAULT_PAGED_ORDER`.
 * For non-paged queries:
 *      Order according to the `orders` in flexQuery, or do not specify the sort when it is empty.
 * For DISTINCT queries:
 *      Only explicit `orders` are applied, never an implicit one — MySQL (error 3065) and
 *      PostgreSQL reject ORDER BY columns outside the DISTINCT select list.
 */
public class OrderByBuilder extends BaseBuilder implements SqlClauseBuilder {

    private Page<?> page;

    public OrderByBuilder(SqlWrapper sqlWrapper, FlexQuery flexQuery) {
        super(sqlWrapper, flexQuery);
    }

    public <T> OrderByBuilder(SqlWrapper sqlWrapper, FlexQuery flexQuery, Page<T> page) {
        super(sqlWrapper, flexQuery);
        this.page = page;
    }

    public void build() {
        // Must be after groupBy processing
        handleOrderBy();
    }

    /**
     * Update sqlWrapper according to whether it is paged and the `orders` attribute of flexQuery
     */
    public void handleOrderBy() {
        Orders orders = flexQuery.getOrders();
        // A DISTINCT projection deduplicates rows, so a column outside its select list has no
        // defined value to sort by — MySQL (error 3065) and PostgreSQL both reject it. Never
        // inject an implicit order into a DISTINCT query; explicit `orders` pass through as-is.
        boolean distinct = flexQuery.isDistinct();
        if (orders == null && !flexQuery.isAggregate() && !distinct) {
            // When `orders` in flexQuery is empty, using the `defaultOrder` configuration of model.
            Orders defaultOrder = ModelManager.getModel(mainModelName).getDefaultOrder();
            if (defaultOrder != null && !defaultOrder.isEmpty()) {
                orders = defaultOrder;
            } else if (page != null && !flexQuery.isAggregate()) {
                // In page query, if the order is not specified, and it is not an aggregate query, use the default order.
                orders = Orders.of(ModelConstant.DEFAULT_PAGED_ORDER);
            }
        }
        if (orders != null) {
            for (List<String> order : orders.getOrderList()) {
                // Support cascade fields (e.g. deptId.managerId.name) and dynamic cascaded
                // field aliases declared on the model (auto-expanded by parseLogicField).
                String aliasField = this.parseLogicField(order.get(0), false);
                sqlWrapper.orderBy(aliasField, order.get(1));
            }
            // For stable order paging queries, if there is no `id` in the orders parameter,
            // automatically add `t.id ASC` at the end of the order condition,
            // to ensure that different page data is as non-repetitive as possible.
            // Skipped for DISTINCT: `id` is outside the select list (same 3065 failure).
            if (page != null && page.isCursorPage() && !distinct && !orders.getFields().contains(ModelConstant.ID)) {
                sqlWrapper.orderBy(SqlWrapper.MAIN_TABLE_ALIAS + "." + ModelConstant.ID, Orders.ASC);
            }
        }
    }

}
