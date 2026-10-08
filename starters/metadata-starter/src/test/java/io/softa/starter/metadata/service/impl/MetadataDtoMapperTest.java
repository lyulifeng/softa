package io.softa.starter.metadata.service.impl;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The cascade-parent flag reaches the client: a form pairs a reference with the one it is filed under
 * from this, the same pairing an import template's dropdowns make on the server.
 */
class MetadataDtoMapperTest {

    private static MetaField relation(boolean cascadeParent) {
        MetaField field = new MetaField();
        ReflectionTestUtils.setField(field, "modelName", "JobLevel");
        ReflectionTestUtils.setField(field, "fieldName", "jobFamilyId");
        ReflectionTestUtils.setField(field, "fieldType", FieldType.MANY_TO_ONE);
        ReflectionTestUtils.setField(field, "relatedModel", "JobFamily");
        ReflectionTestUtils.setField(field, "cascadeParent", cascadeParent);
        return field;
    }

    @Test
    void aDeclaredCascadeParentIsServed() {
        assertThat(MetadataDtoMapper.toFieldDTO(relation(true)).getCascadeParent()).isTrue();
    }

    @Test
    void anUndeclaredRelationIsServedAsNotOne() {
        assertThat(MetadataDtoMapper.toFieldDTO(relation(false)).getCascadeParent()).isFalse();
    }
}
