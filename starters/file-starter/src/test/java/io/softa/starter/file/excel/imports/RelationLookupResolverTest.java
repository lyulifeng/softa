package io.softa.starter.file.excel.imports;

import java.util.*;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.constant.FileConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportFieldDTO;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RelationLookupResolverTest {

    @Test
    void detectLookupGroupsSupportsToOneAndToManyRootFields() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            List<RelationLookupResolver.LookupGroup> groups = resolver.detectLookupGroups(
                    "TestOrder", List.of(importField("deptId.code", null), importField("roleIds.code", null)));

            assertEquals(2, groups.size());
            assertFalse(groups.getFirst().toMany());
            assertTrue(groups.get(1).toMany());
        }
    }

    @Test
    void detectLookupGroupsRejectsNonRelationRootField() {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            assertThrows(IllegalArgumentException.class,
                    () -> resolver.detectLookupGroups("TestOrder", List.of(importField("status.code", null))));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsToOneWritesBackFkId() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "deptId", "Department", List.of("code"), List.of("deptId.code"), true, false, false, null);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("deptId.code", "D001"));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("Department"), eq(List.of("code")), anyCollection(), any()))
                .thenReturn(Map.of(List.of("D001"), 100L));

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(100L, row.get("deptId"));
        assertFalse(row.containsKey("deptId.code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsOneToOneFoldsIntoNestedValueObject() {
        // A OneToOne root owns its sub-record, so the dotted columns are its content, not a business
        // key: they are folded into a nested map the ORM cascade writes inline. Blank cells are left
        // out so "blank means keep the existing value" holds on update.
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "profileId", "Profile", List.of("nickname", "gender"),
                List.of("profileId.nickname", "profileId.gender"), true, false, true, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("profileId.nickname", "Amy");
        row.put("profileId.gender", "  ");

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(Map.of("nickname", "Amy"), row.get("profileId"));
        assertFalse(row.containsKey("profileId.nickname"));
        assertFalse(row.containsKey("profileId.gender"));
        // No lookup is issued for a OneToOne group — nothing to search for.
        verifyNoInteractions(getModelService(resolver));
    }

    @Test
    void resolveRowsOneToOneAllBlankStillYieldsEmptyObject() {
        // The sub-record belongs to the main row: a create must still produce one (the owning FK is
        // typically required), and an update relinks the existing sub-row without touching a field.
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "profileId", "Profile", List.of("nickname"), List.of("profileId.nickname"),
                true, false, true, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("profileId.nickname", "");

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(Map.of(), row.get("profileId"));
        assertFalse(row.containsKey("profileId.nickname"));
    }

    @Test
    void resolveRowsToOneIgnoreEmptyFalseWritesNull() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "deptId", "Department", List.of("code"), List.of("deptId.code"), false, false, false, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("deptId.code", "");

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertTrue(row.containsKey("deptId"));
        assertNull(row.get("deptId"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsToManyWritesBackIdList() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("code"), List.of("roleIds.code"), true, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("roleIds.code", "ADMIN,USER"));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("Role"), eq(List.of("code")), anyCollection(), any()))
                .thenReturn(Map.of(List.of("ADMIN"), 11L, List.of("USER"), 12L));

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(List.of(11L, 12L), row.get("roleIds"));
        assertFalse(row.containsKey("roleIds.code"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsToManyByNameWritesBackIdList() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("name"), List.of("roleIds.name"), true, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("roleIds.name", "Admin,User"));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("Role"), eq(List.of("name")), anyCollection(), any()))
                .thenReturn(Map.of(List.of("Admin"), 21L, List.of("User"), 22L));

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(List.of(21L, 22L), row.get("roleIds"));
        assertFalse(row.containsKey("roleIds.name"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsToManyByCompositeBusinessKeysWritesBackIdList() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("code", "name"), List.of("roleIds.code", "roleIds.name"), true, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleIds.code", "ADMIN,USER");
        row.put("roleIds.name", "Admin,User");

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("Role"), eq(List.of("code", "name")), anyCollection(), any()))
                .thenReturn(Map.of(List.of("ADMIN", "Admin"), 31L, List.of("USER", "User"), 32L));

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(List.of(31L, 32L), row.get("roleIds"));
        assertFalse(row.containsKey("roleIds.code"));
        assertFalse(row.containsKey("roleIds.name"));
    }



    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsToManyMarksFailedWhenCodeNotFound() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("code"), List.of("roleIds.code"), true, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>(Map.of("roleIds.code", "ADMIN,UNKNOWN"));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("Role"), eq(List.of("code")), anyCollection(), any()))
                .thenReturn(Map.of(List.of("ADMIN"), 11L));

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertTrue(row.containsKey(FileConstant.FAILED_REASON));
        assertTrue(row.get(FileConstant.FAILED_REASON).toString().contains("Cannot find Role by code=UNKNOWN"));
        assertFalse(row.containsKey("roleIds"));
    }

    @Test
    void resolveRowsToManyIgnoreEmptyFalseWritesEmptyList() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("code"), List.of("roleIds.code"), false, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleIds.code", "");

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(Collections.emptyList(), row.get("roleIds"));
        assertFalse(row.containsKey("roleIds.code"));
    }

    @Test
    void resolveRowsToManyIgnoreEmptyFalseWithNameWritesEmptyList() {
        RelationLookupResolver resolver = createResolver();
        var group = new RelationLookupResolver.LookupGroup(
                "roleIds", "Role", List.of("name"), List.of("roleIds.name"), false, true, false, null);
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("roleIds.name", "");

        resolver.resolveRows(new ArrayList<>(List.of(row)), List.of(group), true);

        assertEquals(Collections.emptyList(), row.get("roleIds"));
        assertFalse(row.containsKey("roleIds.name"));
    }

    @Test
    void detectLookupGroupsCarriesTheRelationFieldsOwnFilters() {
        // The lookup has to be narrowed to the domain the FK field declares. Department.orgType points
        // at TenantOptionItem and names its option set in `filters`; itemCode is unique inside one set,
        // not across the table, so a lookup that ignores the filter can resolve another set's row — or
        // find two and fail the whole import as a duplicate business key.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            MetaField orgType = metaField("Department", "orgType", FieldType.MANY_TO_ONE, "TenantOptionItem");
            ReflectionTestUtils.setField(orgType, "filters", "[\"optionSetCode\", \"=\", \"OrganizationType\"]");
            mm.when(() -> ModelManager.existField("Department", "orgType")).thenReturn(true);
            mm.when(() -> ModelManager.getModelField("Department", "orgType")).thenReturn(orgType);
            RelationLookupResolver resolver = createResolver();

            List<RelationLookupResolver.LookupGroup> groups = resolver.detectLookupGroups(
                    "Department", List.of(importField("orgType.itemCode", null)));

            assertEquals(1, groups.size());
            assertNotNull(groups.getFirst().relationFilters());
            assertTrue(groups.getFirst().relationFilters().toString().contains("OrganizationType"));
        }
    }

    @Test
    void detectLookupGroupsLeavesFiltersNullWhenTheFieldDeclaresNone() {
        // The common case — a plain FK with no domain to narrow to. Null, not an empty Filters, so the
        // lookup keeps its single unfiltered query shape.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            List<RelationLookupResolver.LookupGroup> groups = resolver.detectLookupGroups(
                    "TestOrder", List.of(importField("deptId.code", null)));

            assertNull(groups.getFirst().relationFilters());
        }
    }

    // ------------------------------------------------- nested relation lookup (3 segments)

    @Test
    void detectLookupGroupsAcceptsAThreeSegmentPathThroughAOneToOne() {
        // The sub-record's own relations were unreachable by anything readable: two segments stop at
        // the relation and the cell must hold the foreign key itself. The third segment names the
        // field to look the target up by.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            List<RelationLookupResolver.LookupGroup> groups = resolver.detectLookupGroups(
                    "Employee",
                    List.of(importField("profileId.notes", null), importField("profileId.idType.name", null)));

            assertEquals(1, groups.size());
            RelationLookupResolver.LookupGroup group = groups.getFirst();
            assertTrue(group.oneToOne());
            assertEquals(1, group.nestedLookups().size());
            RelationLookupResolver.NestedLookup nested = group.nestedLookups().getFirst();
            assertEquals("idType", nested.nestedField());
            assertEquals("IdType", nested.relatedModel());
            assertEquals("name", nested.lookupField());
            assertEquals("profileId.idType.name", nested.dottedPath());
        }
    }

    @Test
    void detectLookupGroupsRejectsThreeSegmentsThroughAManyToOne() {
        // Through a many-to-one the two segments already are the business key of some other row; a
        // third would change what the column means, not extend it.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            assertThrows(IllegalArgumentException.class, () -> resolver.detectLookupGroups(
                    "TestOrder", List.of(importField("deptId.company.name", null))));
        }
    }

    @Test
    void detectLookupGroupsRejectsALeafThatIsItselfARelation() {
        // The leaf is what the cell carries, and a relation cannot be typed into a cell.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            assertThrows(IllegalArgumentException.class, () -> resolver.detectLookupGroups(
                    "Employee", List.of(importField("profileId.idType.country", null))));
        }
    }

    @Test
    void detectLookupGroupsRejectsTheSameNestedFieldAddressedTwice() {
        // profileId.idType holds the raw foreign key, profileId.idType.name resolves one — both write
        // the same nested field, and one of them would silently win.
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            setupModelManager(mm);
            RelationLookupResolver resolver = createResolver();

            assertThrows(IllegalArgumentException.class, () -> resolver.detectLookupGroups(
                    "Employee",
                    List.of(importField("profileId.idType", null), importField("profileId.idType.name", null))));
        }
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsLooksUpANestedRelationByItsNameInOneQuery() {
        RelationLookupResolver resolver = createResolver();
        var group = nestedGroup();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("profileId.notes", "n1", "profileId.idType.name", "NRIC")),
                new LinkedHashMap<>(Map.of("profileId.idType.name", "FIN"))));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("IdType"), eq(List.of("name")), anyCollection(), any()))
                .thenAnswer(inv -> Map.of(List.of("NRIC"), "SG_NRIC", List.of("FIN"), "SG_FIN"));

        resolver.resolveRows(rows, List.of(group), true);

        assertEquals(Map.of("notes", "n1", "idType", "SG_NRIC"), rows.get(0).get("profileId"));
        assertEquals(Map.of("idType", "SG_FIN"), rows.get(1).get("profileId"));
        assertFalse(rows.get(0).containsKey("profileId.idType.name"));
        // One query for the whole sheet, not one per row — the same bargain every lookup strikes.
        verify(typedService, times(1))
                .getIdsByBusinessKeys(eq("IdType"), eq(List.of("name")), anyCollection(), any());
    }

    @Test
    @SuppressWarnings("unchecked")
    void resolveRowsMarksTheRowWhenTheNestedNameMatchesNothing() {
        // A typo must not become a blank foreign key on the sub-record: the row fails and says which
        // value matched nothing, the same contract as every other lookup column.
        RelationLookupResolver resolver = createResolver();
        var group = nestedGroup();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("profileId.idType.name", "NRIIC"))));

        ModelService<Long> typedService = (ModelService<Long>) getModelService(resolver);
        when(typedService.getIdsByBusinessKeys(eq("IdType"), eq(List.of("name")), anyCollection(), any()))
                .thenAnswer(inv -> Map.of());

        resolver.resolveRows(rows, List.of(group), true);

        assertTrue(rows.getFirst().get(FileConstant.FAILED_REASON).toString()
                .contains("Cannot find IdType by name=NRIIC"));
        assertFalse(rows.getFirst().containsKey("profileId.idType.name"));
    }

    @Test
    void resolveRowsLeavesANestedRelationAloneWhenBlank() {
        // Blank keeps the existing value, exactly like every other nested column — an update that
        // does not mention the id type must not clear it.
        RelationLookupResolver resolver = createResolver();
        var group = nestedGroup();
        List<Map<String, Object>> rows = new ArrayList<>(List.of(
                new LinkedHashMap<>(Map.of("profileId.notes", "n1", "profileId.idType.name", " "))));

        resolver.resolveRows(rows, List.of(group), true);

        assertEquals(Map.of("notes", "n1"), rows.getFirst().get("profileId"));
        verifyNoInteractions(getModelService(resolver));
    }

    private RelationLookupResolver.LookupGroup nestedGroup() {
        return new RelationLookupResolver.LookupGroup(
                "profileId", "Profile", List.of("notes", "idType.name"),
                List.of("profileId.notes", "profileId.idType.name"), true, false, true, null,
                List.of(new RelationLookupResolver.NestedLookup(
                        "idType", "IdType", "name", "profileId.idType.name", null)));
    }

    private RelationLookupResolver createResolver() {
        RelationLookupResolver resolver = new RelationLookupResolver();
        ReflectionTestUtils.setField(resolver, "modelService", mock(ModelService.class));
        return resolver;
    }

    private ModelService<?> getModelService(RelationLookupResolver resolver) {
        return (ModelService<?>) ReflectionTestUtils.getField(resolver, "modelService");
    }

    private void setupModelManager(MockedStatic<ModelManager> mm) {
        mm.when(() -> ModelManager.existField("TestOrder", "deptId")).thenReturn(true);
        mm.when(() -> ModelManager.existField("TestOrder", "roleIds")).thenReturn(true);
        mm.when(() -> ModelManager.existField("TestOrder", "status")).thenReturn(true);

        mm.when(() -> ModelManager.getModelField("TestOrder", "deptId"))
                .thenReturn(metaField("TestOrder", "deptId", FieldType.MANY_TO_ONE, "Department"));
        mm.when(() -> ModelManager.getModelField("TestOrder", "roleIds"))
                .thenReturn(metaField("TestOrder", "roleIds", FieldType.MANY_TO_MANY, "Role"));
        mm.when(() -> ModelManager.getModelField("TestOrder", "status"))
                .thenReturn(metaField("TestOrder", "status", FieldType.OPTION, null));

        // Employee → profileId (its own 1:1 sub-record) → Profile → idType (M2O) → IdType
        mm.when(() -> ModelManager.existField("Employee", "profileId")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("Employee", "profileId"))
                .thenReturn(metaField("Employee", "profileId", FieldType.ONE_TO_ONE, "Profile"));
        mm.when(() -> ModelManager.existField("Profile", "idType")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("Profile", "idType"))
                .thenReturn(metaField("Profile", "idType", FieldType.MANY_TO_ONE, "IdType"));
        mm.when(() -> ModelManager.existField("Profile", "notes")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("Profile", "notes"))
                .thenReturn(metaField("Profile", "notes", FieldType.STRING, null));
        mm.when(() -> ModelManager.existField("IdType", "name")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("IdType", "name"))
                .thenReturn(metaField("IdType", "name", FieldType.STRING, null));
        mm.when(() -> ModelManager.existField("IdType", "country")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("IdType", "country"))
                .thenReturn(metaField("IdType", "country", FieldType.MANY_TO_ONE, "CountryRegion"));

        // Everything past deptId is deliberately VALID: the rejection test below must fail on the
        // root's field type alone, or removing that gate would surface as a different error and the
        // test could not tell the difference.
        mm.when(() -> ModelManager.existField("Department", "company")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("Department", "company"))
                .thenReturn(metaField("Department", "company", FieldType.MANY_TO_ONE, "LegalEntity"));
        mm.when(() -> ModelManager.existField("LegalEntity", "name")).thenReturn(true);
        mm.when(() -> ModelManager.getModelField("LegalEntity", "name"))
                .thenReturn(metaField("LegalEntity", "name", FieldType.STRING, null));
    }

    private ImportFieldDTO importField(String fieldName, Boolean ignoreEmpty) {
        ImportFieldDTO dto = new ImportFieldDTO();
        dto.setFieldName(fieldName);
        dto.setHeader(fieldName);
        dto.setIgnoreEmpty(ignoreEmpty);
        return dto;
    }

    private MetaField metaField(String modelName, String fieldName, FieldType fieldType, String relatedModel) {
        MetaField mf = new MetaField();
        ReflectionTestUtils.setField(mf, "modelName", modelName);
        ReflectionTestUtils.setField(mf, "fieldName", fieldName);
        ReflectionTestUtils.setField(mf, "fieldType", fieldType);
        ReflectionTestUtils.setField(mf, "label", fieldName);
        if (relatedModel != null) {
            ReflectionTestUtils.setField(mf, "relatedModel", relatedModel);
        }
        return mf;
    }
}
