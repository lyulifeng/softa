package io.softa.starter.permission.scope;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.when;

/**
 * Path-selection unit tests for {@link DefaultDepartmentCascadePathResolver}.
 *
 * <p>Focus is the fix for a <b>dynamic cascaded</b> {@code departmentId} (a
 * derived field with no physical column, e.g. OvertimeRequest / LeaveRequest,
 * whose {@code cascadedField} is {@code employeeId.departmentId}): it must
 * resolve to that cascade path — NOT the bare {@code departmentId} anchor —
 * otherwise the department-scope filter emits SQL against a non-existent
 * {@code <table>.department_id} column and fails. A physically-backed
 * {@code departmentId} still uses the direct anchor.
 */
class DefaultDepartmentCascadePathResolverTest {

    private final DepartmentCascadePathResolver resolver = new DefaultDepartmentCascadePathResolver();

    /**
     * A ToOne (MANY_TO_ONE) field mock; {@code cascadedField} is only read when
     * dynamic. Built into a local BEFORE any {@code mockStatic().when()} chain —
     * stubbing it inline inside {@code thenReturn(...)} nests stubbings and trips
     * Mockito's UnfinishedStubbing check.
     */
    private static MetaField toOne(String relatedModel, boolean dynamicCascaded, String cascadedField) {
        MetaField f = mock(MetaField.class);
        when(f.getFieldType()).thenReturn(FieldType.MANY_TO_ONE);
        when(f.getRelatedModel()).thenReturn(relatedModel);
        when(f.isDynamicCascadedField()).thenReturn(dynamicCascaded);
        when(f.getCascadedField()).thenReturn(cascadedField);
        return f;
    }

    @Test
    void dynamicDepartmentId_resolvesToItsCascadePath() {
        MetaField dept = toOne("Department", true, "employeeId.departmentId");
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModelFieldOrNull("OvertimeRequest", "departmentId"))
                    .thenReturn(dept);

            assertThat(resolver.resolve("OvertimeRequest")).contains("employeeId.departmentId");
        }
    }

    @Test
    void physicalDepartmentId_resolvesToDirectAnchor() {
        MetaField dept = toOne("Department", false, null);
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModelFieldOrNull("DirectDeptModel", "departmentId"))
                    .thenReturn(dept);

            assertThat(resolver.resolve("DirectDeptModel")).contains("departmentId");
        }
    }

    @Test
    void noDirectDeptButEmployeeAnchor_resolvesViaEmployee() {
        // No direct departmentId on the model (unstubbed → null), but employeeId
        // points at Employee, which itself has a departmentId anchor.
        MetaField employeeFk = toOne("Employee", false, null);
        MetaField employeeDeptFk = toOne("Department", false, null);
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModelFieldOrNull("EmpChildModel", "employeeId"))
                    .thenReturn(employeeFk);
            mm.when(() -> ModelManager.getModelFieldOrNull("Employee", "departmentId"))
                    .thenReturn(employeeDeptFk);

            assertThat(resolver.resolve("EmpChildModel")).contains("employeeId.departmentId");
        }
    }

    @Test
    void noDepartmentAnchorAtAll_resolvesEmpty() {
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            // All getModelFieldOrNull calls return null (unstubbed default).
            assertThat(resolver.resolve("NoDeptModel")).isEmpty();
        }
    }
}
