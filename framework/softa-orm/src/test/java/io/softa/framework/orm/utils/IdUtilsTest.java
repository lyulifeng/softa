package io.softa.framework.orm.utils;

import java.io.Serializable;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mockStatic;

class IdUtilsTest {

    @Test
    void convertIdToLong() {
        assertNull(IdUtils.convertIdToLong(null));
        assertEquals(1L, IdUtils.convertIdToLong(1));
        assertEquals(1L, IdUtils.convertIdToLong(1));
        assertEquals(1L, IdUtils.convertIdToLong("1"));
        assertEquals(1L, IdUtils.convertIdToLong(1L));
    }

    private static MetaField field(FieldType type, String relatedModel, FieldType relatedFieldType) {
        MetaField field = new MetaField();
        ReflectionTestUtils.setField(field, "fieldType", type);
        ReflectionTestUtils.setField(field, "relatedModel", relatedModel);
        ReflectionTestUtils.setField(field, "relatedFieldType", relatedFieldType);
        return field;
    }

    /**
     * A join model whose sides are relations: the ids a client sends are strings, and they have to come
     * out as the Long ids the join table holds, or a many-to-many Remove matches nothing.
     */
    @Test
    void aToOneFieldTakesTheTypeOfTheIdItReferences() {
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelField("Rel", "userId"))
                    .thenReturn(field(FieldType.MANY_TO_ONE, "User", FieldType.LONG));
            models.when(() -> ModelManager.getModelField("Rel", "countryId"))
                    .thenReturn(field(FieldType.MANY_TO_ONE, "Country", FieldType.STRING));

            List<Serializable> userIds = IdUtils.formatIds("Rel", "userId", List.of("12", 13L));
            assertEquals(List.of(12L, 13L), userIds);
            assertEquals(12L, (Long) IdUtils.formatId("Rel", "userId", "12"));
            // A code-as-id master stays a string.
            assertEquals(List.of("SG"), IdUtils.formatIds("Rel", "countryId", List.of("SG")));
        }
    }

    @Test
    void aToOneFieldWithoutItsColumnTypeReadsTheReferencedId() {
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelField("Rel", "roleId"))
                    .thenReturn(field(FieldType.ONE_TO_ONE, "Role", null));
            models.when(() -> ModelManager.getModelField("Role", ModelConstant.ID))
                    .thenReturn(field(FieldType.LONG, null, null));

            assertEquals(List.of(7L), IdUtils.formatIds("Rel", "roleId", List.of("7")));
        }
    }

    @Test
    void aPlainIdFieldIsConvertedAsBefore() {
        try (MockedStatic<ModelManager> models = mockStatic(ModelManager.class)) {
            models.when(() -> ModelManager.getModelField("Rel", ModelConstant.ID))
                    .thenReturn(field(FieldType.LONG, null, null));

            assertEquals(List.of(5L), IdUtils.formatIds("Rel", List.of("5")));
        }
    }
}
