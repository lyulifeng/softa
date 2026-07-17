package io.softa.starter.permission.scope;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.scope.contributor.DepartmentSubtreeScopeContributor;
import io.softa.starter.permission.scope.contributor.ManagedDepartmentsScopeContributor;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Regression lock for the OvertimeRequest bug: the department-scope contributors
 * must compile their filter on the resolved cascade path
 * ({@code employeeId.departmentId.idPath}), NOT a bare {@code departmentId.idPath}
 * — the latter renders SQL against the non-existent overtime_request.department_id
 * column. Pairs with {@link DefaultDepartmentCascadePathResolverTest} (which
 * proves the resolver returns the cascade path for a dynamic departmentId).
 */
class DepartmentScopeFilterPathTest {

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;
    private static final String EXPECTED_FIELD = "employeeId.departmentId.idPath";

    private final DepartmentCascadePathResolver cascade = mock(DepartmentCascadePathResolver.class);
    private final DepartmentIdPathResolver idPath = mock(DepartmentIdPathResolver.class);

    @Test
    void deptSubtree_usesResolvedCascadePathInFilter() {
        when(cascade.resolve("OvertimeRequest")).thenReturn(Optional.of("employeeId.departmentId"));
        when(idPath.idPathOf(5L)).thenReturn(Optional.of("1/5"));

        ObjectNode expr = JSON.objectNode();
        expr.put("deptId", "5"); // static root → no ContextHolder needed
        ScopeRule rule = new ScopeRule();
        rule.setScopeExpr(expr);

        Filters filters = new DepartmentSubtreeScopeContributor(cascade, idPath)
                .compile(rule, "OvertimeRequest");

        assertThat(filters.toString()).contains(EXPECTED_FIELD);
    }

    @Test
    void managedDepartments_usesResolvedCascadePathInFilter() {
        when(cascade.resolve("OvertimeRequest")).thenReturn(Optional.of("employeeId.departmentId"));
        when(idPath.idPathsOf(any())).thenReturn(List.of("1/5"));

        ArrayNode deptIds = JSON.arrayNode();
        deptIds.add("5");
        ObjectNode expr = JSON.objectNode();
        expr.set("deptIds", deptIds); // static roots → no ContextHolder needed
        ScopeRule rule = new ScopeRule();
        rule.setScopeExpr(expr);

        Filters filters = new ManagedDepartmentsScopeContributor(cascade, idPath)
                .compile(rule, "OvertimeRequest");

        assertThat(filters.toString()).contains(EXPECTED_FIELD);
    }
}
