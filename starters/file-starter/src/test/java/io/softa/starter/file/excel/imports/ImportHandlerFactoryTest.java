package io.softa.starter.file.excel.imports;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.file.dto.ImportFieldDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.excel.imports.handler.BaseImportHandler;
import io.softa.starter.file.excel.imports.handler.DateHandler;
import io.softa.starter.file.excel.imports.handler.TimeHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImportHandlerFactoryTest {

    @Test
    void createHandlerUsesTimeHandlerForTimeFields() throws Exception {
        ImportHandlerFactory factory = new ImportHandlerFactory();
        MetaField metaField = new MetaField();
        ReflectionTestUtils.setField(metaField, "fieldType", FieldType.TIME);
        ReflectionTestUtils.setField(metaField, "label", "Clock In");
        ReflectionTestUtils.setField(metaField, "fieldName", "clockIn");
        ReflectionTestUtils.setField(metaField, "modelName", "attendance");

        ImportFieldDTO importFieldDTO = new ImportFieldDTO();
        importFieldDTO.setFieldName("clockIn");

        Method createHandler = ImportHandlerFactory.class
                .getDeclaredMethod("createHandler", MetaField.class, ImportFieldDTO.class);
        createHandler.setAccessible(true);

        BaseImportHandler handler = (BaseImportHandler) createHandler.invoke(factory, metaField, importFieldDTO);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("clockIn", "2:30");
        handler.handleRow(row);

        assertInstanceOf(TimeHandler.class, handler);
        assertEquals("02:30:00", row.get("clockIn"));
    }

    // ------------------------------------------------- nested OneToOne sub-fields
    // A OneToOne dotted path writes the related row inline instead of looking an existing one up, so
    // its cells still need the conversion the flat columns get. The metadata comes from the related
    // model while the column stays keyed by the dotted path.

    @Test
    void createHandlersConvertsNestedOneToOneSubFields() {
        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            stubRelation(modelManager, "Employee", "profileId", FieldType.ONE_TO_ONE, "Profile");
            modelManager.when(() -> ModelManager.existField("Profile", "dateOfBirth")).thenReturn(true);
            modelManager.when(() -> ModelManager.getModelField("Profile", "dateOfBirth"))
                    .thenReturn(metaField("Profile", "dateOfBirth", FieldType.DATE, null));

            List<BaseImportHandler> handlers = new ImportHandlerFactory()
                    .createHandlers(templateFor("Employee", "profileId.dateOfBirth"));

            assertEquals(1, handlers.size());
            assertInstanceOf(DateHandler.class, handlers.getFirst());

            Map<String, Object> row = new LinkedHashMap<>();
            row.put("profileId.dateOfBirth", "1990/1/5");
            handlers.getFirst().handleRow(row);

            // Read and written back under the dotted key — the value object is assembled later.
            assertEquals("1990-01-05", row.get("profileId.dateOfBirth"));
        }
    }

    @Test
    void createHandlersSkipsManyToOneLookupPaths() {
        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            stubRelation(modelManager, "Employee", "deptId", FieldType.MANY_TO_ONE, "Department");

            List<BaseImportHandler> handlers = new ImportHandlerFactory()
                    .createHandlers(templateFor("Employee", "deptId.code"));

            // Still a relation lookup: RelationLookupResolver owns the column, not a handler.
            assertTrue(handlers.isEmpty());
        }
    }

    @Test
    void createHandlersSkipsOneToOneWithoutRelatedModel() {
        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            stubRelation(modelManager, "Employee", "profileId", FieldType.ONE_TO_ONE, "  ");

            List<BaseImportHandler> handlers = new ImportHandlerFactory()
                    .createHandlers(templateFor("Employee", "profileId.nickname"));

            // Misconfigured template: no handler here, so RelationLookupResolver reports it by name.
            assertTrue(handlers.isEmpty());
        }
    }

    private static void stubRelation(MockedStatic<ModelManager> modelManager, String modelName,
                                     String fieldName, FieldType fieldType, String relatedModel) {
        modelManager.when(() -> ModelManager.existField(modelName, fieldName)).thenReturn(true);
        modelManager.when(() -> ModelManager.getModelField(modelName, fieldName))
                .thenReturn(metaField(modelName, fieldName, fieldType, relatedModel));
    }

    private static ImportTemplateDTO templateFor(String modelName, String fieldName) {
        ImportFieldDTO importFieldDTO = new ImportFieldDTO();
        importFieldDTO.setFieldName(fieldName);
        ImportTemplateDTO templateDTO = new ImportTemplateDTO();
        templateDTO.setModelName(modelName);
        templateDTO.setImportFields(List.of(importFieldDTO));
        return templateDTO;
    }

    private static MetaField metaField(String modelName, String fieldName, FieldType fieldType, String relatedModel) {
        MetaField metaField = new MetaField();
        ReflectionTestUtils.setField(metaField, "modelName", modelName);
        ReflectionTestUtils.setField(metaField, "fieldName", fieldName);
        ReflectionTestUtils.setField(metaField, "fieldType", fieldType);
        ReflectionTestUtils.setField(metaField, "relatedModel", relatedModel);
        return metaField;
    }
}
