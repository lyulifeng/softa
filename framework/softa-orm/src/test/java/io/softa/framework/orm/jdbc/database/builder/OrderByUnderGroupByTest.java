package io.softa.framework.orm.jdbc.database.builder;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.AggFunctions;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.enums.AggFunctionType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.jdbc.database.SqlWrapper;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;

import static org.junit.jupiter.api.Assertions.*;

/**
 * A grouped query may only be sorted by what it grouped on.
 *
 * <p>Grouping collapses rows, so a column the query did not group on has no single value per
 * returned row. PostgreSQL answers that with 42803 and MySQL with `only_full_group_by`; neither
 * picks a row for you. {@link OrderByBuilder} therefore drops such fields instead of emitting them.
 *
 * <p>This is not a corner case. A list client sends its standing default order — a creation
 * timestamp, typically — alongside whatever grouping the user just picked, and that timestamp is
 * never among the grouped fields. Before the filter, every grouped list query was rejected by the
 * database, whatever field the user grouped on, on every model that has a default order.
 *
 * <p>The builders run in the order {@code SqlBuilderFactory} uses: aggregation first, because it is
 * what decides the grouped field set, then ORDER BY, which stays inside it.
 */
class OrderByUnderGroupByTest {

    private static final String MAIN_MODEL = "Order";
    private static final String MAIN_TABLE = "orders";

    /** The failure as it reached production: group by one field, sort by the client's default. */
    @Test
    void ordersOutsideTheGroupedSetAreDropped() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            stubMainModel(mm);
            stubStoredField(mm, "status", "status_code");
            stubStoredField(mm, "createdTime", "created_time");
            stubNumericField(mm, "amount", "amount");
            mm.when(() -> ModelManager.getModelStoredNumericFields(MAIN_MODEL))
                    .thenReturn(new HashSet<>(Set.of("amount")));
            mm.when(() -> ModelManager.getModelFieldColumn(MAIN_MODEL, "amount")).thenReturn("amount");

            SqlWrapper sqlWrapper = new SqlWrapper(MAIN_MODEL);
            FlexQuery flexQuery = new FlexQuery();
            flexQuery.setGroupBy(List.of("status"));
            flexQuery.setFields(List.of("status", "amount"));
            flexQuery.setOrders(Orders.ofDesc("createdTime"));

            build(sqlWrapper, flexQuery);

            assertTrue(groupByClause(sqlWrapper).contains("t.status_code"),
                    "status must be grouped, got: " + groupByClause(sqlWrapper));
            assertEquals("", orderByClause(sqlWrapper),
                    "created_time is not grouped on, so it cannot be sorted by");
        }
    }

    /** Sorting by a grouped field is legal and must survive. */
    @Test
    void ordersOnGroupedFieldsAreKept() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            stubMainModel(mm);
            stubStoredField(mm, "status", "status_code");
            mm.when(() -> ModelManager.getModelStoredNumericFields(MAIN_MODEL)).thenReturn(new HashSet<>());

            SqlWrapper sqlWrapper = new SqlWrapper(MAIN_MODEL);
            FlexQuery flexQuery = new FlexQuery();
            flexQuery.setGroupBy(List.of("status"));
            flexQuery.setOrders(Orders.ofDesc("status"));

            build(sqlWrapper, flexQuery);

            assertEquals("t.status_code DESC,", orderByClause(sqlWrapper));
        }
    }

    /**
     * A selected field is grouped on too — {@code AggregateBuilder} adds the whole select list to
     * GROUP BY — so sorting by one stays legal and must not be dropped along with the rest.
     */
    @Test
    void ordersOnSelectedFieldsSurviveBecauseThoseAreGroupedToo() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            stubMainModel(mm);
            stubStoredField(mm, "status", "status_code");
            stubStoredField(mm, "channel", "channel_code");
            mm.when(() -> ModelManager.getModelStoredNumericFields(MAIN_MODEL)).thenReturn(new HashSet<>());

            SqlWrapper sqlWrapper = new SqlWrapper(MAIN_MODEL);
            FlexQuery flexQuery = new FlexQuery();
            flexQuery.setGroupBy(List.of("status"));
            flexQuery.setFields(List.of("status", "channel"));
            flexQuery.setOrders(Orders.ofDesc("channel"));

            build(sqlWrapper, flexQuery);

            assertEquals("t.channel_code DESC,", orderByClause(sqlWrapper));
        }
    }

    /** Without grouping nothing is restricted — the filter must not touch ordinary queries. */
    @Test
    void ordinaryQueriesKeepEveryOrder() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            stubMainModel(mm);
            stubStoredField(mm, "createdTime", "created_time");

            SqlWrapper sqlWrapper = new SqlWrapper(MAIN_MODEL);
            FlexQuery flexQuery = new FlexQuery();
            flexQuery.setFields(List.of("createdTime"));
            flexQuery.setOrders(Orders.ofDesc("createdTime"));

            build(sqlWrapper, flexQuery);

            assertEquals("t.created_time DESC,", orderByClause(sqlWrapper));
        }
    }

    /** Aggregation without any GROUP BY leaves one row: there is nothing left to sort by. */
    @Test
    void aggregationWithoutGroupingDropsEveryOrder() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            stubMainModel(mm);
            stubStoredField(mm, "createdTime", "created_time");
            stubNumericField(mm, "amount", "amount");

            SqlWrapper sqlWrapper = new SqlWrapper(MAIN_MODEL);
            FlexQuery flexQuery = new FlexQuery();
            flexQuery.setAggFunctions(AggFunctions.of(AggFunctionType.SUM, "amount"));
            flexQuery.setOrders(Orders.ofDesc("createdTime"));

            build(sqlWrapper, flexQuery);

            assertEquals("", orderByClause(sqlWrapper));
        }
    }

    /** Builders in the order SqlBuilderFactory runs them: grouping decides, ordering obeys. */
    private static void build(SqlWrapper sqlWrapper, FlexQuery flexQuery) {
        new AggregateBuilder(sqlWrapper, flexQuery).build();
        new OrderByBuilder(sqlWrapper, flexQuery).build();
    }

    private static void stubMainModel(MockedStatic<ModelManager> mm) {
        MetaModel mainModel = new MetaModel();
        ReflectionTestUtils.setField(mainModel, "modelName", MAIN_MODEL);
        ReflectionTestUtils.setField(mainModel, "tableName", MAIN_TABLE);
        mm.when(() -> ModelManager.getModel(MAIN_MODEL)).thenReturn(mainModel);
        mm.when(() -> ModelManager.isTimelineModel(Mockito.anyString())).thenReturn(false);
        mm.when(() -> ModelManager.isMultiTenantControl(Mockito.anyString())).thenReturn(false);
    }

    private static void stubStoredField(MockedStatic<ModelManager> mm, String fieldName, String columnName) {
        stubField(mm, fieldName, columnName, FieldType.STRING);
    }

    private static void stubNumericField(MockedStatic<ModelManager> mm, String fieldName, String columnName) {
        stubField(mm, fieldName, columnName, FieldType.LONG);
    }

    private static void stubField(MockedStatic<ModelManager> mm, String fieldName,
                                  String columnName, FieldType fieldType) {
        MetaField field = new MetaField();
        ReflectionTestUtils.setField(field, "modelName", MAIN_MODEL);
        ReflectionTestUtils.setField(field, "fieldName", fieldName);
        ReflectionTestUtils.setField(field, "fieldType", fieldType);
        ReflectionTestUtils.setField(field, "columnName", columnName);
        mm.when(() -> ModelManager.existField(MAIN_MODEL, fieldName)).thenReturn(true);
        mm.when(() -> ModelManager.getModelField(MAIN_MODEL, fieldName)).thenReturn(field);
    }

    private static String groupByClause(SqlWrapper sqlWrapper) {
        Object clause = ReflectionTestUtils.getField(sqlWrapper, "groupByClause");
        return clause == null ? "" : clause.toString();
    }

    private static String orderByClause(SqlWrapper sqlWrapper) {
        Object clause = ReflectionTestUtils.getField(sqlWrapper, "orderByClause");
        return clause == null ? "" : clause.toString();
    }
}
