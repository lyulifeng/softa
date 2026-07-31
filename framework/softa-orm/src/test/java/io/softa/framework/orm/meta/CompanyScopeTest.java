package io.softa.framework.orm.meta;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.jdbc.JdbcService;
import io.softa.framework.orm.scope.CompanyScope;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the per-company narrowing: the boot-time resolution of which field (or path) a model reaches
 * its company through, and every way the narrowing is skipped.
 *
 * <p>The skips carry the risk. Each one is silent by design — an over-eager condition empties a list
 * the user needs rather than merely showing too much — so this test is the only thing separating
 * "correctly not narrowed" from "quietly stopped narrowing". Builds a real frozen snapshot through
 * {@code init()} with a mocked {@link JdbcService}, mirroring {@code MultiCountryScopeTest}, because
 * both the validation and the narrowing read snapshot internals.
 */
class CompanyScopeTest {

    private static Object previousSnapshot;

    @BeforeAll
    static void initSnapshot() throws Exception {
        if (SystemConfig.env == null) {
            SystemConfig.env = new SystemConfig();
        }
        previousSnapshot = snapshotField().get(null);
        initWith(models(), fields());
    }

    @AfterAll
    static void restoreSnapshot() throws Exception {
        snapshotField().set(null, previousSnapshot);
    }

    // ---- boot validation -------------------------------------------------

    @Test
    void aModelWithNeitherAReferenceNorAPathIsRejectedAtInit() throws Exception {
        // Otherwise the flag is a no-op and the model silently keeps showing every company's rows —
        // indistinguishable from never having marked it, which is the failure this mechanism removes.
        Object good = snapshotField().get(null);
        try {
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(companyScoped("Orphan", "orphan"), legalEntity())),
                    new ArrayList<>(List.of(
                            field("Orphan", "id", "id", FieldType.LONG),
                            field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG)))));
            assertTrue(e.getMessage().contains("neither references"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void twoCompanyReferencesRequireTheFieldToBeDeclared() throws Exception {
        // "Narrow by which one?" is not a question to answer by picking the first — a payroll account
        // referencing both an owning and a paying entity would silently pick a side.
        Object good = snapshotField().get(null);
        try {
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(companyScoped("TwoRefs", "two_refs"), legalEntity())),
                    new ArrayList<>(List.of(
                            field("TwoRefs", "id", "id", FieldType.LONG),
                            companyRef("TwoRefs", "legalEntityId"),
                            companyRef("TwoRefs", "payingEntityId"),
                            field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG)))));
            assertTrue(e.getMessage().contains("declare"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void anUnresolvablePathIsRejectedAtInit() throws Exception {
        Object good = snapshotField().get(null);
        try {
            MetaModel bad = companyScoped("BadPath", "bad_path");
            bad.setCompanyField("deptId.noSuchField");
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(bad, model("Department", "department"), legalEntity())),
                    new ArrayList<>(List.of(
                            field("BadPath", "id", "id", FieldType.LONG),
                            deptRef("BadPath", "deptId"),
                            field("Department", "id", "id", FieldType.LONG),
                            field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG)))));
            assertTrue(e.getMessage().contains("does not resolve"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void aPathLandingOnTheWrongModelIsRejectedAtInit() throws Exception {
        // The nastiest misconfiguration: the path resolves and the filter runs, comparing company ids
        // against another model's ids. It matches nothing, and reads as missing data.
        Object good = snapshotField().get(null);
        try {
            MetaModel bad = companyScoped("WrongLeaf", "wrong_leaf");
            bad.setCompanyField("deptId.managerId");
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(bad, model("Department", "department"),
                            model("Employee", "employee"), legalEntity())),
                    new ArrayList<>(List.of(
                            field("WrongLeaf", "id", "id", FieldType.LONG),
                            deptRef("WrongLeaf", "deptId"),
                            field("Department", "id", "id", FieldType.LONG),
                            employeeRef("Department", "managerId"),
                            field("Employee", "id", "id", FieldType.LONG),
                            field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG)))));
            assertTrue(e.getMessage().contains("rather than"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void theCompanyModelItselfCannotBeCompanyScoped() throws Exception {
        // Self-scoping would reduce the company switcher to the company already selected — the
        // company list is the one thing that must never be narrowed by the selection.
        Object good = snapshotField().get(null);
        try {
            MetaModel selfScoped = companyScoped(ModelConstant.COMPANY_MODEL, "legal_entity");
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(selfScoped)),
                    new ArrayList<>(List.of(
                            field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG)))));
            assertTrue(e.getMessage().contains("IS the company"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void aDirectReferenceIsDerivedAndADeclaredPathIsKept() {
        assertEquals("legalEntityId", ModelManager.getModel("Department").getCompanyField());
        assertEquals("deptId.legalEntityId", ModelManager.getModel("DeptStats").getCompanyField());
    }

    @Test
    void aPlainModelKeepsNoCompanyField() {
        // Carrying a legalEntityId is not the same as belonging to one company for scoping purposes —
        // the flag is what says so, and without it nothing is resolved or narrowed.
        assertFalse(ModelManager.getModel("Unscoped").isCompanyScoped());
        assertNull(ModelManager.getModel("Unscoped").getCompanyField());
    }

    // ---- narrowing -------------------------------------------------------

    @Test
    void narrowsACompanyScopedModelByTheContextCompany() {
        Filters result = withCompany(8712L,
                () -> CompanyScope.append("Department", Filters.of("active", Operator.EQUAL, true)));

        assertTrue(Filters.containsField(result, "legalEntityId"));
        // The bound value stays a placeholder: FilterUnitParser substitutes it when building SQL, so
        // the compiled Filters must carry the token rather than the resolved id.
        assertTrue(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void narrowsAStatisticThroughItsDeclaredPath() {
        // The whole reason companyField exists: a per-department statistic has no company of its own,
        // and reaching one through the department is what makes it filterable without a new column.
        Filters result = withCompany(8712L, () -> CompanyScope.append("DeptStats", new Filters()));

        assertTrue(Filters.containsField(result, "deptId.legalEntityId"));
        assertTrue(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void doesNotNarrowAReadThatNamesRowsById() {
        // XToOneGroupProcessor expands a stored ManyToOne with searchList(relatedModel, id IN (…)).
        // Narrowing that makes a row pointing at another company's department render blank while the
        // header sits elsewhere — silently, since a missing row is not an error.
        Filters byId = Filters.of(ModelConstant.ID, Operator.IN, List.of(1L, 2L));

        Filters result = withCompany(8712L, () -> CompanyScope.append("Department", byId));

        assertSame(byId, result);
        assertFalse(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void anIdConditionCombinedWithOthersAlsoSkipsNarrowing() {
        // The expansion path adds an active clause alongside the id set, so the check has to see
        // through a composite condition rather than only a bare one.
        Filters byIdAndActive = Filters.of(ModelConstant.ID, Operator.IN, List.of(1L))
                .and("active", Operator.EQUAL, true);

        Filters result = withCompany(8712L, () -> CompanyScope.append("Department", byIdAndActive));

        assertFalse(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void aPickerQueryWithNoIdIsStillNarrowed() {
        // The counterpart: choosing among candidates never filters by id, so the id exemption must
        // not have turned the narrowing off in general.
        Filters result = withCompany(8712L,
                () -> CompanyScope.append("Department", Filters.of("active", Operator.EQUAL, true)));

        assertTrue(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void leavesAnUnscopedModelUntouched() {
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, withCompany(8712L, () -> CompanyScope.append("Unscoped", original)));
    }

    @Test
    void skipsWhenNoCompanyIsSelected() {
        // Anonymous and public endpoints, service-to-service calls, and a tenant that has not created
        // its first company. Narrowing to nothing would empty every list on the way to creating one.
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, ContextHolder.callWith(new Context(),
                () -> CompanyScope.append("Department", original)));
    }

    @Test
    void doesNotOverrideACompanyTheCallerAlreadyPassed() {
        // What lets a form scope its dropdowns by the company picked in the form rather than the one
        // in the header. AND-ing would compare two different ids and always match nothing.
        Filters callerScoped = Filters.of("legalEntityId", Operator.EQUAL, 99L);

        Filters result = withCompany(8712L, () -> CompanyScope.append("Department", callerScoped));

        assertSame(callerScoped, result);
        assertFalse(result.toString().contains(EnvConstant.SELECTED_COMP_ID), result.toString());
    }

    @Test
    void anUnknownModelFallsThrough() {
        // Sits on the generic read path: an unknown model must reach the query that reports it, not
        // fail here with an unrelated metadata error.
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, withCompany(8712L, () -> CompanyScope.append("NoSuchModel", original)));
        assertSame(original, withCompany(8712L, () -> CompanyScope.append(null, original)));
    }

    @Test
    void theSelectionNarrowsWithinAMultiCompanyGrantRatherThanSkipping() {
        // The composition that decides whether the company switch works at all for a role granted
        // several companies. ModelServiceImpl.scopedAccess feeds these selections INTO
        // appendScopeAccessFilters instead of wrapping its result, precisely so this holds: at the
        // point the selection runs, a grant like `legalEntityId IN (A, B, C)` has not been added yet.
        //
        // Wrapping the output instead would make that grant indistinguishable from a caller that
        // already picked a company — the selection would skip, and the user would see all three
        // companies at once with the switch doing nothing. This test fails if anyone reorders it.
        Filters callerFilters = Filters.of("active", Operator.EQUAL, true);

        Filters selected = withCompany(8712L, () -> CompanyScope.append("Department", callerFilters));
        // Then the permission layer ANDs the grant on top, exactly as scopedAccess does.
        Filters granted = Filters.and(selected,
                Filters.of("legalEntityId", Operator.IN, List.of(8712L, 9001L, 9002L)));

        assertTrue(granted.toString().contains(EnvConstant.SELECTED_COMP_ID), granted.toString());
        // Both terms present: the selection is a subset of the grant, so this resolves to one company
        // rather than to nothing.
        assertTrue(granted.toString().contains("9001"), granted.toString());
    }

    @Test
    void narrowsIndependentlyOfTheCountryNarrowing() {
        // The two compose on a model that is both: different fields, so neither swallows the other.
        // Chained in this order by ModelServiceImpl.scopedAccess.
        Context context = new Context();
        context.setSelectedCompanyId(8712L);
        context.setSelectedCompanyCountry("SG");
        Filters result = ContextHolder.callWith(context, () ->
                CompanyScope.append("BothScoped",
                        io.softa.framework.orm.scope.MultiCountryScope.append("BothScoped", new Filters())));

        assertTrue(Filters.containsField(result, "legalEntityId"), result.toString());
        assertTrue(Filters.containsField(result, "country"), result.toString());
    }

    // ---- fixture ---------------------------------------------------------

    /** Runs {@code op} with a context carrying the given selected company. */
    private static Filters withCompany(Long companyId, java.util.function.Supplier<Filters> op) {
        Context context = new Context();
        context.setSelectedCompanyId(companyId);
        return ContextHolder.callWith(context, op::get);
    }

    private static Field snapshotField() throws Exception {
        Field field = ModelManager.class.getDeclaredField("snapshot");
        field.setAccessible(true);
        return field;
    }

    private static void initWith(List<MetaModel> models, List<MetaField> fields) {
        try {
            JdbcService<?> jdbcService = Mockito.mock(JdbcService.class);
            Mockito.when(jdbcService.selectMetaEntityList("SysModel", MetaModel.class, null)).thenReturn(models);
            Mockito.when(jdbcService.selectMetaEntityList("SysField", MetaField.class, null)).thenReturn(fields);
            ModelManager modelManager = new ModelManager();
            Field jdbc = ModelManager.class.getDeclaredField("jdbcService");
            jdbc.setAccessible(true);
            jdbc.set(modelManager, jdbcService);
            modelManager.init();
        } catch (ReflectiveOperationException e) {
            throw new IllegalStateException(e);
        }
    }

    // ArrayList, not List.of: ListUtils.allNotNull probes contains(null), which immutable
    // collections reject with NPE.
    private static List<MetaModel> models() {
        MetaModel stats = companyScoped("DeptStats", "dept_stats");
        stats.setCompanyField("deptId.legalEntityId");
        MetaModel both = companyScoped("BothScoped", "both_scoped");
        both.setMultiCountry(true);
        return new ArrayList<>(List.of(
                companyScoped("Department", "department"),
                stats,
                both,
                model("Unscoped", "unscoped"),
                legalEntity(),
                model(ModelConstant.COUNTRY_REGION_MODEL, "country_region")));
    }

    private static List<MetaField> fields() {
        return new ArrayList<>(List.of(
                field("Department", "id", "id", FieldType.LONG),
                companyRef("Department", "legalEntityId"),
                field("Department", "active", "active", FieldType.BOOLEAN),
                field("DeptStats", "id", "id", FieldType.LONG),
                deptRef("DeptStats", "deptId"),
                field("BothScoped", "id", "id", FieldType.LONG),
                companyRef("BothScoped", "legalEntityId"),
                countryRef("BothScoped", "country"),
                // An unscoped model may carry a company reference too — it records which company the
                // row relates to, and must not be mistaken for one whose rows belong to a company.
                field("Unscoped", "id", "id", FieldType.LONG),
                companyRef("Unscoped", "legalEntityId"),
                field("Unscoped", "active", "active", FieldType.BOOLEAN),
                field(ModelConstant.COMPANY_MODEL, "id", "id", FieldType.LONG),
                field(ModelConstant.COUNTRY_REGION_MODEL, "id", "id", FieldType.STRING)));
    }

    private static MetaModel model(String modelName, String tableName) {
        MetaModel metaModel = new MetaModel();
        metaModel.setModelName(modelName);
        metaModel.setLabel(modelName);
        metaModel.setTableName(tableName);
        return metaModel;
    }

    private static MetaModel companyScoped(String modelName, String tableName) {
        MetaModel metaModel = model(modelName, tableName);
        metaModel.setCompanyScoped(true);
        return metaModel;
    }

    private static MetaModel legalEntity() {
        return model(ModelConstant.COMPANY_MODEL, "legal_entity");
    }

    private static MetaField field(String modelName, String fieldName, String columnName, FieldType type) {
        MetaField metaField = new MetaField();
        metaField.setModelName(modelName);
        metaField.setFieldName(fieldName);
        metaField.setColumnName(columnName);
        metaField.setLabel(fieldName);
        metaField.setFieldType(type);
        return metaField;
    }

    private static MetaField relation(String modelName, String fieldName, String relatedModel) {
        MetaField metaField = field(modelName, fieldName, fieldName, FieldType.MANY_TO_ONE);
        metaField.setRelatedModel(relatedModel);
        return metaField;
    }

    private static MetaField companyRef(String modelName, String fieldName) {
        return relation(modelName, fieldName, ModelConstant.COMPANY_MODEL);
    }

    private static MetaField deptRef(String modelName, String fieldName) {
        return relation(modelName, fieldName, "Department");
    }

    private static MetaField employeeRef(String modelName, String fieldName) {
        return relation(modelName, fieldName, "Employee");
    }

    private static MetaField countryRef(String modelName, String fieldName) {
        return relation(modelName, fieldName, ModelConstant.COUNTRY_REGION_MODEL);
    }
}
