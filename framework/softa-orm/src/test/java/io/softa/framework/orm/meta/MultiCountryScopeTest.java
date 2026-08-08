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
import io.softa.framework.orm.scope.MultiCountryScope;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Covers the per-country narrowing of multi-country models: the boot-time validation that
 * resolves which field carries the partition, and the four ways the narrowing can be skipped.
 *
 * <p>Skipping is where the risk lives. Every skip is silent by design — an over-eager
 * condition would empty a required dropdown, which is worse than an unfiltered one — so the
 * only thing standing between "correctly not narrowed" and "silently stopped working" is this
 * test. Builds a real frozen snapshot through {@code init()} with a mocked {@link JdbcService},
 * mirroring {@code ModelManagerCopyableTest}, because both the validation and the narrowing
 * read snapshot internals.
 */
class MultiCountryScopeTest {

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
    void multiCountryModelWithoutACountryReferenceIsRejectedAtInit() throws Exception {
        // Without this the narrowing would quietly do nothing, which is indistinguishable from
        // a model nobody ever marked — the exact failure this mechanism exists to remove.
        Object good = snapshotField().get(null);
        try {
            MetaModel orphan = multiCountryModel("NoCountry", "no_country");
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(orphan)),
                    new ArrayList<>(List.of(field("NoCountry", "id", "id", FieldType.STRING)))));
            assertTrue(e.getMessage().contains("must declare"), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void aSecondCountryReferenceIsNotAnAnchor() throws Exception {
        // A value domain partitioned by country can still record another country as an attribute —
        // the country that issued a pass type is not the country the row belongs to. Only the field
        // named `country` is the partition, and without it boot fails rather than picking one.
        Object good = snapshotField().get(null);
        try {
            RuntimeException e = assertThrows(RuntimeException.class, () -> initWith(
                    new ArrayList<>(List.of(multiCountryModel("TwoCountries", "two_countries"),
                            countryRegion())),
                    new ArrayList<>(List.of(
                            field("TwoCountries", "id", "id", FieldType.STRING),
                            countryRef("TwoCountries", "issuingCountry"),
                            countryRef("TwoCountries", "residenceCountry"),
                            field("CountryRegion", "id", "id", FieldType.STRING)))));
            assertTrue(e.getMessage().contains(ModelConstant.COUNTRY_FIELD), e.getMessage());
        } finally {
            snapshotField().set(null, good);
        }
    }

    @Test
    void aToManyCountryReferenceIsNotAnAnchor() {
        // A bank serving many countries is not partitioned by them — that field is an attribute, and
        // the model is simply not multi-country. If one ever needs to be, the partition is a single
        // `country` field (a dynamic cascaded one when the country is reached through another model).
        assertFalse(ModelManager.getModel("BankLike").isMultiCountry());
    }

    @Test
    void aPlainModelIsNotOnTheCountryAxis() {
        assertFalse(ModelManager.getModel("Employee").isMultiCountry());
    }

    // ---- narrowing -------------------------------------------------------

    @Test
    void narrowsAMultiCountryModelByTheContextCountry() {
        Filters result = withCountry("SG",
                () -> MultiCountryScope.append("PassType", Filters.of("active", Operator.EQUAL, true)));

        assertTrue(Filters.containsField(result, "country"));
        // The bound value stays a placeholder: FilterUnitParser substitutes it when building SQL,
        // so the compiled Filters must carry the token, not the resolved 'SG'.
        assertTrue(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
    }

    @Test
    void narrowsWithTheResolvedCountryWhenNoCompanyIsSelected() {
        // A role granted no company selects nothing, so no header goes out — and it is the one caller
        // whose country is never in doubt, since the enricher falls back to the company it belongs to.
        // That is a self-service employee, and without this it sees every country's value domains.
        //
        // The value has to be the resolved country rather than the placeholder, even though both mean
        // the same country here: FilterUnitParser resolves SELECTED_COMP_COUNTRY to null when nothing
        // is selected — on purpose, so a CUSTOM scope rule naming it cannot silently start matching the
        // caller's own country — and emitting the token here would compile to country = NULL, matching
        // nothing. Empty is worse than unnarrowed: it blanks a required dropdown.
        Filters result = withFallbackCountry("SG",
                () -> MultiCountryScope.append("PassType", Filters.of("active", Operator.EQUAL, true)));

        assertTrue(Filters.containsField(result, "country"));
        assertFalse(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
        assertTrue(result.toString().contains("SG"), result.toString());
    }

    @Test
    void doesNotNarrowAReadThatNamesRowsById() {
        // The bug this guards: XToOneGroupProcessor expands a stored ManyToOne by issuing
        // searchList(relatedModel, id IN (…)). Narrowing that by country makes an employee whose pass
        // type was recorded under a New Zealand company render blank while the header sits on a
        // Singapore one — silently, since a missing row is not an error. Its FilterControl.bypassAll()
        // does not cover this: that only waives active-control and soft-delete.
        Filters byId = Filters.of(ModelConstant.ID, Operator.IN, List.of("NZ_AEWV", "SG_EP"));

        Filters result = withCountry("SG", () -> MultiCountryScope.append("PassType", byId));

        assertSame(byId, result);
        assertFalse(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
    }

    @Test
    void anIdConditionCombinedWithOthersAlsoSkipsNarrowing() {
        // Same reason, but the expansion path adds a soft-delete clause alongside the id set, so the
        // check has to see through a composite condition rather than only a bare one.
        Filters byIdAndDeleted = Filters.of(ModelConstant.ID, Operator.IN, List.of("NZ_AEWV"))
                .and("active", Operator.EQUAL, true);

        Filters result = withCountry("SG", () -> MultiCountryScope.append("PassType", byIdAndDeleted));

        assertFalse(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
    }

    @Test
    void aPickerQueryWithNoIdIsStillNarrowed() {
        // The counterpart: choosing among candidates never filters by id, so the fix must not have
        // turned the narrowing off in general.
        Filters result = withCountry("SG",
                () -> MultiCountryScope.append("PassType", Filters.of("active", Operator.EQUAL, true)));

        assertTrue(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
    }

    @Test
    void leavesAPlainModelUntouched() {
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, withCountry("SG", () -> MultiCountryScope.append("Employee", original)));
    }

    @Test
    void skipsWhenTheContextCarriesNoCountry() {
        // Anonymous (public form) and service-to-service contexts have no selected company.
        // Narrowing to nothing here would empty a required dropdown on the pre-boarding form.
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, ContextHolder.callWith(new Context(),
                () -> MultiCountryScope.append("PassType", original)));
    }

    @Test
    void doesNotOverrideACountryTheCallerAlreadyPassed() {
        // This is what lets a create-employee or transfer form scope its dropdowns by the legal
        // entity picked in the form, which may differ from the one the header is switched to.
        // AND-ing instead would yield country = 'SG' AND country = 'NZ' — always empty.
        Filters callerScoped = Filters.of("country", Operator.EQUAL, "NZ");

        Filters result = withCountry("SG", () -> MultiCountryScope.append("PassType", callerScoped));

        assertSame(callerScoped, result);
        assertFalse(result.toString().contains(EnvConstant.COMPANY_COUNTRY), result.toString());
    }

    @Test
    void anUnknownModelFallsThrough() {
        // Sits on the generic read path: an unknown model must reach the query that reports it,
        // not fail here with an unrelated metadata error.
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertSame(original, withCountry("SG", () -> MultiCountryScope.append("NoSuchModel", original)));
        assertSame(original, withCountry("SG", () -> MultiCountryScope.append(null, original)));
    }

    // ---- fixture ---------------------------------------------------------

    /** Runs {@code op} with a context carrying the given selected-company country. */
    private static Filters withCountry(String country, java.util.function.Supplier<Filters> op) {
        Context context = new Context();
        context.setCompanyId(8712L);
        context.setCompanyCountry(country);
        return ContextHolder.callWith(context, op::get);
    }

    /**
     * Runs {@code op} with a country but no selection — what the enricher leaves behind when it falls
     * back to the caller's own company. The only state in which the two fields disagree.
     */
    private static Filters withFallbackCountry(String country, java.util.function.Supplier<Filters> op) {
        Context context = new Context();
        context.setCompanyCountry(country);
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

    // ArrayList, not List.of: ListUtils.allNotNull probes contains(null), which
    // immutable collections reject with NPE.
    private static List<MetaModel> models() {
        return new ArrayList<>(List.of(
                multiCountryModel("PassType", "pass_type"),
                // Serves many countries but is not partitioned by them — an attribute, not an axis.
                model("BankLike", "bank_like"),
                model("Employee", "employee"),
                model("BankLikeCountryRel", "bank_like_country_rel"),
                countryRegion()));
    }

    private static List<MetaField> fields() {
        return new ArrayList<>(List.of(
                field("PassType", "id", "id", FieldType.STRING),
                countryRef("PassType", "country"),
                field("PassType", "code", "code", FieldType.STRING),
                field("PassType", "active", "active", FieldType.BOOLEAN),
                field("BankLike", "id", "id", FieldType.LONG),
                countriesManyToMany("BankLike", "countries"),
                field("Employee", "id", "id", FieldType.LONG),
                // A plain model may carry a country too — it records which country the row
                // belongs to, and must not be mistaken for a partition.
                countryRef("Employee", "country"),
                field("Employee", "active", "active", FieldType.BOOLEAN),
                field("BankLikeCountryRel", "id", "id", FieldType.LONG),
                field("BankLikeCountryRel", "bankLikeId", "bank_like_id", FieldType.LONG),
                field("BankLikeCountryRel", "countryId", "country_id", FieldType.STRING),
                field("CountryRegion", "id", "id", FieldType.STRING)));
    }

    private static MetaModel model(String modelName, String tableName) {
        MetaModel metaModel = new MetaModel();
        metaModel.setModelName(modelName);
        metaModel.setLabel(modelName);
        metaModel.setTableName(tableName);
        return metaModel;
    }

    private static MetaModel multiCountryModel(String modelName, String tableName) {
        MetaModel metaModel = model(modelName, tableName);
        metaModel.setMultiCountry(true);
        return metaModel;
    }

    private static MetaModel countryRegion() {
        return model(ModelConstant.COUNTRY_REGION_MODEL, "country_region");
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

    private static MetaField countryRef(String modelName, String fieldName) {
        MetaField metaField = field(modelName, fieldName, "country", FieldType.MANY_TO_ONE);
        metaField.setRelatedModel(ModelConstant.COUNTRY_REGION_MODEL);
        return metaField;
    }

    /** Mirrors the shape Bank.countries ships with: a join model naming both sides. */
    private static MetaField countriesManyToMany(String modelName, String fieldName) {
        MetaField metaField = field(modelName, fieldName, fieldName, FieldType.MANY_TO_MANY);
        metaField.setRelatedModel(ModelConstant.COUNTRY_REGION_MODEL);
        metaField.setJoinModel("BankLikeCountryRel");
        metaField.setJoinLeft("bankLikeId");
        metaField.setJoinRight("countryId");
        return metaField;
    }
}
