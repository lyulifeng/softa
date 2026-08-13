package io.softa.starter.file.excel.imports;

import java.lang.reflect.Method;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.ValidationException;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.file.dto.ImportFieldDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.excel.imports.handler.BaseImportHandler;
import io.softa.starter.file.excel.imports.handler.DateHandler;
import io.softa.starter.file.excel.imports.handler.DefaultHandler;
import io.softa.starter.file.excel.imports.handler.NumberHandler;
import io.softa.starter.file.excel.imports.handler.RelationIdHandler;
import io.softa.starter.file.excel.imports.handler.TimeHandler;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertThrows;
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

    // ------------------------------------------------- readable failures on typed columns
    // Before these handlers existed the cell text travelled to the write and the ORM's own conversion
    // threw, so the row's Failed Reason was the JDK's bare `For input string: "..."` — no column name,
    // which on a wide template leaves the user with nothing to fix.

    @Test
    void aNumericColumnReportsTheColumnNameNotJustTheValue() throws Exception {
        MetaField metaField = metaField(FieldType.INTEGER, "Sequence", "sequence");
        BaseImportHandler handler = invokeCreateHandler(metaField, importFieldDTO("sequence"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("sequence", "abc");

        assertInstanceOf(NumberHandler.class, handler);
        ValidationException e = assertThrows(ValidationException.class, () -> handler.handleRow(row));
        assertTrue(e.getMessage().contains("Sequence"), e.getMessage());
        assertTrue(e.getMessage().contains("abc"), e.getMessage());
    }

    @Test
    void aNumericColumnConvertsWhatItAccepts() throws Exception {
        MetaField metaField = metaField(FieldType.LONG, "Head Count", "headCount");
        BaseImportHandler handler = invokeCreateHandler(metaField, importFieldDTO("headCount"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("headCount", " 42 ");

        handler.handleRow(row);

        assertEquals(42L, row.get("headCount"));
    }

    @Test
    void aBareNumericFkColumnPointsAtTheLookupPathItShouldHaveUsed() throws Exception {
        // The reported case: Department.orgType is a bare FK column onto TenantOptionItem, and the value
        // pasted in was the display name the detail page shows ("Branch / Branch").
        try (MockedStatic<ModelManager> modelManager = Mockito.mockStatic(ModelManager.class)) {
            MetaField metaField = metaField(FieldType.MANY_TO_ONE, "Organization Type", "orgType");
            ReflectionTestUtils.setField(metaField, "relatedModel", "TenantOptionItem");
            ReflectionTestUtils.setField(metaField, "relatedFieldType", FieldType.LONG);
            MetaModel related = new MetaModel();
            ReflectionTestUtils.setField(related, "businessKey", List.of("itemCode"));
            modelManager.when(() -> ModelManager.existModel("TenantOptionItem")).thenReturn(true);
            modelManager.when(() -> ModelManager.getModel("TenantOptionItem")).thenReturn(related);

            BaseImportHandler handler = invokeCreateHandler(metaField, importFieldDTO("orgType"));
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("orgType", "Branch / Branch");

            assertInstanceOf(RelationIdHandler.class, handler);
            ValidationException e = assertThrows(ValidationException.class, () -> handler.handleRow(row));
            assertTrue(e.getMessage().contains("Organization Type"), e.getMessage());
            assertTrue(e.getMessage().contains("TenantOptionItem"), e.getMessage());
            assertTrue(e.getMessage().contains("orgType.itemCode"), e.getMessage());
        }
    }

    @Test
    void aCodeAsIdFkColumnIsLeftAlone() throws Exception {
        // CountryRegion's id IS the ISO code, so "SG" in a bare `country` column is the correct id and
        // must not be validated as a number.
        MetaField metaField = metaField(FieldType.MANY_TO_ONE, "Country", "country");
        ReflectionTestUtils.setField(metaField, "relatedModel", "CountryRegion");
        ReflectionTestUtils.setField(metaField, "relatedFieldType", FieldType.STRING);

        BaseImportHandler handler = invokeCreateHandler(metaField, importFieldDTO("country"));
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("country", "SG");
        handler.handleRow(row);

        assertInstanceOf(DefaultHandler.class, handler);
        assertEquals("SG", row.get("country"));
    }

    private static MetaField metaField(FieldType fieldType, String label, String fieldName) {
        MetaField metaField = new MetaField();
        ReflectionTestUtils.setField(metaField, "fieldType", fieldType);
        ReflectionTestUtils.setField(metaField, "label", label);
        ReflectionTestUtils.setField(metaField, "fieldName", fieldName);
        ReflectionTestUtils.setField(metaField, "modelName", "Department");
        return metaField;
    }

    private static ImportFieldDTO importFieldDTO(String fieldName) {
        ImportFieldDTO dto = new ImportFieldDTO();
        dto.setFieldName(fieldName);
        return dto;
    }

    private static BaseImportHandler invokeCreateHandler(MetaField metaField, ImportFieldDTO dto) throws Exception {
        Method createHandler = ImportHandlerFactory.class
                .getDeclaredMethod("createHandler", MetaField.class, ImportFieldDTO.class);
        createHandler.setAccessible(true);
        return (BaseImportHandler) createHandler.invoke(new ImportHandlerFactory(), metaField, dto);
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
