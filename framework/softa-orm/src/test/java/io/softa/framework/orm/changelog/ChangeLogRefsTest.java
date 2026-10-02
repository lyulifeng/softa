package io.softa.framework.orm.changelog;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mockStatic;

/**
 * What a change log records about the records its row pointed at.
 *
 * <p>A row's history has to be findable from its parent, and the payload cannot answer that: it is
 * stored as an unindexed string. Above all once the row is gone — a deleted family member has no id
 * left to be asked about, but its log can still say which employee it belonged to.
 */
class ChangeLogRefsTest {

    private static MetaField field(String name, FieldType type) {
        // Setters are package-private: metadata is loaded, never built by hand outside the package.
        MetaField field = new MetaField();
        ReflectionTestUtils.setField(field, "fieldName", name);
        ReflectionTestUtils.setField(field, "fieldType", type);
        return field;
    }

    private static final List<MetaField> FAMILY_MEMBER = List.of(
            field("id", FieldType.LONG),
            field("employeeId", FieldType.MANY_TO_ONE),
            field("relationshipId", FieldType.MANY_TO_ONE),
            field("name", FieldType.STRING),
            field("tagIds", FieldType.MANY_TO_MANY));

    @Test
    void namesEachRecordTheRowPointedAt() {
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelFields("EmpFamilyMember")).thenReturn(FAMILY_MEMBER);

            List<String> refs = ChangeLogPublisherImpl.refsOf("EmpFamilyMember",
                    Map.of("id", 301L, "employeeId", 100L, "relationshipId", 7L, "name", "A"), null);

            assertThat(refs).containsExactly("employeeId=100", "relationshipId=7");
        }
    }

    @Test
    void findsAMovedRowUnderBothParents() {
        // An update's before is the whole original row and its after only what changed: a row moved
        // from one employee to another belongs in both histories.
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelFields("EmpFamilyMember")).thenReturn(FAMILY_MEMBER);

            List<String> refs = ChangeLogPublisherImpl.refsOf("EmpFamilyMember",
                    Map.of("employeeId", 100L, "relationshipId", 7L), Map.of("employeeId", 200L));

            assertThat(refs).containsExactlyInAnyOrder("employeeId=100", "employeeId=200", "relationshipId=7");
        }
    }

    @Test
    void readsTheIdOffAnExpandedReference() {
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelFields("EmpFamilyMember")).thenReturn(FAMILY_MEMBER);

            List<String> refs = ChangeLogPublisherImpl.refsOf("EmpFamilyMember",
                    Map.of("employeeId", Map.of("id", 100L, "displayName", "Ada")), null);

            assertThat(refs).containsExactly("employeeId=100");
        }
    }

    @Test
    void carriesNothingWhenTheRowPointsAtNothing() {
        // Only many-to-one: a many-to-many value is a list of other rows, not this row's parent.
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelFields("EmpFamilyMember")).thenReturn(FAMILY_MEMBER);

            assertThat(ChangeLogPublisherImpl.refsOf("EmpFamilyMember",
                    Map.of("name", "A", "tagIds", List.of(1L, 2L)), null)).isNull();
        }
    }
}
