package io.softa.starter.es.service.impl;

import java.lang.reflect.Method;
import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.data.elasticsearch.core.query.Criteria;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.Filters;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * Filter trees reach Elasticsearch with their grouping intact: nested AND and OR groups render
 * as nested bool queries, and no operand is dropped on the way.
 */
class EsFilterConversionTest {

    private final ChangeLogServiceImpl service = new ChangeLogServiceImpl();

    /** The query Elasticsearch would be sent, compacted to {@code field=value} terms. */
    private String query(Filters filters) throws Exception {
        Criteria criteria = service.convertFilters(filters);
        Class<?> processor = Class.forName("org.springframework.data.elasticsearch.client.elc.CriteriaQueryProcessor");
        Method createQuery = processor.getDeclaredMethod("createQuery", Criteria.class);
        createQuery.setAccessible(true);
        return String.valueOf(createQuery.invoke(null, criteria))
                .replaceAll("\\{\"query_string\":\\{\"default_operator\":\"and\",\"fields\":\\[\"(\\w+)\"],\"query\":\"([^\"]*)\"}}", "$1=$2")
                .replaceAll("\\{\"bool\":\\{\"must\":\\[([^\\[\\]{}]*)]}}", "must($1)")
                .replaceAll("\\{\"bool\":\\{\"should\":\\[([^\\[\\]{}]*)]}}", "should($1)");
    }

    @Test
    void aFlatConjunctionRendersEveryCondition() throws Exception {
        String q = query(new Filters().eq("model", "Employee").eq("rowId", "1"));
        assertThat(q).contains("model=Employee").contains("rowId=1").doesNotContain("should");
    }

    @Test
    void aConjunctionNestedInAConjunctionIsAccepted() throws Exception {
        Filters filters = Filters.and(new Filters().eq("model", "Employee").eq("rowId", "1"),
                new Filters().eq("tenantId", 7L));
        assertThat(query(filters)).contains("model=Employee").contains("rowId=1").contains("tenantId=7");
    }

    @Test
    void aDisjunctionNestedInAConjunctionKeepsEveryOperand() throws Exception {
        Filters either = Filters.or();
        either.setChildren(new java.util.ArrayList<>(List.of(Filters.of("rowId", Operator.EQUAL, "1"),
                Filters.of("rowId", Operator.EQUAL, "2"))));
        String q = query(Filters.and(either, new Filters().eq("tenantId", 7L)));
        assertThat(q).contains("should(must(rowId=1),must(rowId=2))").contains("tenantId=7");
    }

    @Test
    void aDisjunctionOfConjunctionsKeepsBothGroups() throws Exception {
        Filters either = Filters.or();
        either.setChildren(new java.util.ArrayList<>(List.of(new Filters().eq("a", "1").eq("b", "2"),
                new Filters().eq("c", "3").eq("d", "4"))));
        String q = query(either);
        assertThat(q).startsWith("Query: {\"bool\":{\"should\":[")
                .contains("must(a=1),must(b=2)").contains("must(c=3),must(d=4)");
    }

    @Test
    void anEmptyFilterAddsNoCondition() throws Exception {
        // No query at all, which the search then runs as match-all.
        assertThat(query(new Filters())).isEqualTo("null");
    }
}
