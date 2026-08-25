package io.softa.starter.file.excel.export.support;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.MetaOptionItem;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.meta.OptionManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportFieldDTO;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The half of the resolver that decides which set a column belongs to.
 *
 * <p>A relation onto {@code TenantOptionItem} names its option set in the field's own filters, and the
 * dropdown is only correct if that name is read back out. Read the wrong thing and the column offers
 * another set's codes — a list that looks plausible and imports as an unresolvable value.
 *
 * <p>The routing — which of the four shapes a column is, and what each one asks for — is exercised
 * against a stubbed metadata snapshot. What it decides is visible in the query it issues, so the
 * assertions are on that: which model, which field, and what it is narrowed by.
 */
class OptionDropdownResolverTest {

    private String optionSetCodeIn(Filters filters) throws Exception {
        Method method = OptionDropdownResolver.class
                .getDeclaredMethod("optionSetCodeIn", Filters.class);
        method.setAccessible(true);
        return (String) method.invoke(new OptionDropdownResolver(), filters);
    }

    @Test
    void readsTheSetOutOfASingleEqualityFilter() throws Exception {
        // The shape every such field declares today: ["optionSetCode", "=", "OrganizationType"].
        assertThat(optionSetCodeIn(Filters.of("optionSetCode", Operator.EQUAL, "OrganizationType")))
                .isEqualTo("OrganizationType");
    }

    @Test
    void findsItAlongsideAnotherCondition() throws Exception {
        // A filter is a tree. Reading a fixed position works until a second condition is added and
        // pushes the one that matters out of place, so the search has to walk it.
        Filters filters = Filters.of("activeFlag", Operator.EQUAL, true)
                .and(Filters.of("optionSetCode", Operator.EQUAL, "ProjectType"));

        assertThat(optionSetCodeIn(filters)).isEqualTo("ProjectType");
    }

    @Test
    void answersNullWhenNoConditionNamesASet() throws Exception {
        // Nothing to narrow by means the list would be every option item the tenant owns, across every
        // set — so the column gets no dropdown rather than a misleading one.
        assertThat(optionSetCodeIn(Filters.of("activeFlag", Operator.EQUAL, true))).isNull();
        assertThat(optionSetCodeIn(null)).isNull();
        assertThat(optionSetCodeIn(new Filters())).isNull();
    }

    // ---------------------------------------------------------------- routing

    @Test
    void aBooleanColumnOffersTheLabelsRatherThanTrueAndFalse() {
        // Yes / No is what the export writes and what a person reading the sheet expects; the import
        // handler takes either form. Read from the platform's own boolean set so the two sides cannot
        // drift apart.
        withMetadata(mm -> {
            field("JobGrade", "active", FieldType.BOOLEAN, null, null, null);
            optionSet(BaseConstant.BOOLEAN_OPTION_SET_CODE, List.of(item("true", "Yes"), item("false", "No")));

            assertThat(resolve("JobGrade", null, "active")).containsEntry(0, List.of("Yes", "No"));
            verifyNoInteractions(modelService);
        });
    }

    @Test
    void reachesThroughAOneToOneIntoAnEnum() {
        // `employeeProfileId.gender` is two hops but one dotted level: the root is a sub-record, the
        // leaf an option field on it. Not following it was why every template built on a one-to-one
        // carried no dropdowns at all.
        withMetadata(mm -> {
            field("Employee", "employeeProfileId", FieldType.ONE_TO_ONE, "EmployeeProfile", null, null);
            field("EmployeeProfile", "gender", FieldType.OPTION, null, "Gender", null);
            optionSet("Gender", List.of(item("male", "Male"), item("female", "Female")));

            assertThat(resolve("Employee", null, "employeeProfileId.gender"))
                    .containsEntry(0, List.of("male", "female"));
            verifyNoInteractions(modelService);
        });
    }

    @Test
    void aRelationColumnAsksTheTargetModelForTheFieldTheColumnNames() {
        // `bankId.name` offers Bank.name — the very value RelationLookupResolver reverse-looks-up on
        // the way back in, so a sheet filled from its own dropdown imports without translation.
        withMetadata(mm -> {
            field("Employee", "bankId", FieldType.MANY_TO_ONE, "Bank", null, null);
            field("Bank", "name", FieldType.STRING, null, null, null);
            model("Bank", false);
            stubRows("Bank", "name", List.of("DBS BANK LTD", "OCBC"));

            assertThat(resolve("Employee", null, "bankId.name"))
                    .containsEntry(0, List.of("DBS BANK LTD", "OCBC"));
            assertThat(capturedQuery("Bank").getFields()).containsExactly("name");
            assertThat(capturedQuery("Bank").isDistinct()).isTrue();
        });
    }

    @Test
    void narrowsACountryScopedModelByTheTemplatesOwnCountry() {
        // The template declares the country it is for. An administrator of an SG company downloading
        // the NZ template must get NZ values, so the country cannot come from the request context.
        withMetadata(mm -> {
            field("Employee", "passTypeId", FieldType.MANY_TO_ONE, "PassType", null, null);
            field("PassType", "name", FieldType.STRING, null, null, null);
            model("PassType", true);
            fieldExists("PassType", "country");
            stubRows("PassType", "name", List.of("Work Permit"));

            resolve("Employee", "NZ", "passTypeId.name");

            assertThat(capturedQuery("PassType").getFilters())
                    .hasToString("[\"country\",\"=\",\"NZ\"]");
        });
    }

    @Test
    void leavesAModelThatDoesNotVaryByCountryUnnarrowed() {
        // Filtering on a column the model does not have would fail the query, which costs the whole
        // dropdown rather than narrowing it.
        withMetadata(mm -> {
            field("Employee", "jobGradeId", FieldType.MANY_TO_ONE, "JobGrade", null, null);
            field("JobGrade", "name", FieldType.STRING, null, null, null);
            model("JobGrade", false);
            stubRows("JobGrade", "name", List.of("G1"));

            resolve("Employee", "SG", "jobGradeId.name");

            assertThat(Filters.isEmpty(capturedQuery("JobGrade").getFilters())).isTrue();
        });
    }

    @Test
    void twoColumnsWantingTheSameValuesShareOneQuery() {
        // A template picks a project team twice — default and additional. Asking twice would double
        // the cost of every such pair for no difference in the answer.
        withMetadata(mm -> {
            field("Employee", "projectTeamId", FieldType.MANY_TO_ONE, "ProjectTeam", null, null);
            field("Employee", "additionalProjectTeamIds", FieldType.MANY_TO_MANY, "ProjectTeam", null, null);
            field("ProjectTeam", "name", FieldType.STRING, null, null, null);
            model("ProjectTeam", false);
            stubRows("ProjectTeam", "name", List.of("Alpha"));

            Map<Integer, List<String>> resolved =
                    resolve("Employee", null, "projectTeamId.name", "additionalProjectTeamIds.name");

            assertThat(resolved).containsEntry(0, List.of("Alpha")).containsEntry(1, List.of("Alpha"));
            verify(modelService, times(1)).searchList(eq("ProjectTeam"), any(FlexQuery.class));
        });
    }

    @Test
    void aBareRelationColumnGetsNoDropdown() {
        // Addressed without a dotted path the cell holds a raw foreign key. A list of ids is not
        // something anyone can pick from, and a list of labels would look right and fail on import.
        withMetadata(mm -> {
            field("Employee", "bankId", FieldType.MANY_TO_ONE, "Bank", null, null);

            assertThat(resolve("Employee", null, "bankId")).isEmpty();
            verifyNoInteractions(modelService);
        });
    }

    @Test
    void ignoresAPathWithASecondDot() {
        // The import side rejects a two-level cascade outright, so a dropdown there would be values
        // for a column that cannot import.
        withMetadata(mm -> {
            field("Employee", "deptId", FieldType.MANY_TO_ONE, "Department", null, null);

            assertThat(resolve("Employee", null, "deptId.companyId.code")).isEmpty();
            verifyNoInteractions(modelService);
        });
    }

    // ---------------------------------------------------------------- harness

    private final ModelService<?> modelService = mock(ModelService.class);
    private final Map<String, MetaField> fields = new LinkedHashMap<>();
    private final Map<String, MetaModel> models = new LinkedHashMap<>();
    private final Map<String, List<MetaOptionItem>> optionSets = new LinkedHashMap<>();
    private final List<String> extraFields = new ArrayList<>();
    private final Map<String, FlexQuery> queriesByModel = new LinkedHashMap<>();

    /**
     * Runs the body with {@link ModelManager} and {@link OptionManager} answering from the maps above.
     * Both are static gateways onto a snapshot that only exists in a running application.
     */
    private void withMetadata(java.util.function.Consumer<MockedStatic<ModelManager>> body) {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class);
             MockedStatic<OptionManager> om = Mockito.mockStatic(OptionManager.class)) {
            mm.when(() -> ModelManager.getModelFieldOrNull(any(), any()))
                    .thenAnswer(inv -> fields.get(inv.getArgument(0) + "." + inv.getArgument(1)));
            mm.when(() -> ModelManager.existModel(any())).thenAnswer(inv -> models.containsKey(inv.getArgument(0)));
            mm.when(() -> ModelManager.getModel(any())).thenAnswer(inv -> models.get(inv.getArgument(0)));
            mm.when(() -> ModelManager.existField(any(), any()))
                    .thenAnswer(inv -> extraFields.contains(inv.getArgument(0) + "." + inv.getArgument(1))
                            || fields.containsKey(inv.getArgument(0) + "." + inv.getArgument(1)));
            om.when(() -> OptionManager.existsOptionSetCode(any()))
                    .thenAnswer(inv -> optionSets.containsKey(inv.getArgument(0)));
            om.when(() -> OptionManager.getMetaOptionItems(any()))
                    .thenAnswer(inv -> optionSets.getOrDefault(inv.getArgument(0), List.of()));
            body.accept(mm);
        }
    }

    private Map<Integer, List<String>> resolve(String modelName, String country, String... columns) {
        OptionDropdownResolver resolver = new OptionDropdownResolver();
        ReflectionTestUtils.setField(resolver, "modelService", modelService);
        List<ImportFieldDTO> importFields = new ArrayList<>();
        for (String column : columns) {
            ImportFieldDTO dto = new ImportFieldDTO();
            dto.setFieldName(column);
            importFields.add(dto);
        }
        return resolver.resolve(modelName, importFields, country);
    }

    /**
     * A metadata field, populated reflectively.
     *
     * <p>{@link MetaField}'s setters are package-private on purpose — the snapshot is built at startup
     * and not meant to be written from elsewhere. Widening them so a test can call them would trade a
     * real constraint for a convenience.
     */
    private void field(String modelName, String fieldName, FieldType fieldType,
                       String relatedModel, String optionSetCode, String filters) {
        MetaField metaField = new MetaField();
        ReflectionTestUtils.setField(metaField, "modelName", modelName);
        ReflectionTestUtils.setField(metaField, "fieldName", fieldName);
        ReflectionTestUtils.setField(metaField, "fieldType", fieldType);
        ReflectionTestUtils.setField(metaField, "relatedModel", relatedModel);
        ReflectionTestUtils.setField(metaField, "optionSetCode", optionSetCode);
        ReflectionTestUtils.setField(metaField, "filters", filters);
        fields.put(modelName + "." + fieldName, metaField);
    }

    private void model(String modelName, boolean multiCountry) {
        MetaModel metaModel = new MetaModel();
        ReflectionTestUtils.setField(metaModel, "modelName", modelName);
        ReflectionTestUtils.setField(metaModel, "multiCountry", multiCountry);
        models.put(modelName, metaModel);
    }

    private void fieldExists(String modelName, String fieldName) {
        extraFields.add(modelName + "." + fieldName);
    }

    private void optionSet(String optionSetCode, List<MetaOptionItem> items) {
        optionSets.put(optionSetCode, items);
    }

    private static MetaOptionItem item(String itemCode, String label) {
        MetaOptionItem optionItem = new MetaOptionItem();
        ReflectionTestUtils.setField(optionItem, "itemCode", itemCode);
        ReflectionTestUtils.setField(optionItem, "label", label);
        return optionItem;
    }

    @SuppressWarnings("unchecked")
    private void stubRows(String modelName, String fieldName, List<String> values) {
        List<Map<String, Object>> rows = new ArrayList<>();
        values.forEach(value -> {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put(fieldName, value);
            rows.add(row);
        });
        when(((ModelService<Long>) modelService).searchList(eq(modelName), any(FlexQuery.class)))
                .thenAnswer(inv -> {
                    queriesByModel.put(inv.getArgument(0), inv.getArgument(1));
                    return rows;
                });
    }

    /** The query the resolver actually issued for a model — what its routing decision is visible as. */
    private FlexQuery capturedQuery(String modelName) {
        FlexQuery flexQuery = queriesByModel.get(modelName);
        assertThat(flexQuery).as("no query was issued for %s", modelName).isNotNull();
        return flexQuery;
    }
}
