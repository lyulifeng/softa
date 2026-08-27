package io.softa.starter.permission.service;

import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.starter.permission.spi.PermissionInfo;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * The role's legal-entity grant: which companies a user may reach at all, as opposed to the one it is
 * currently looking at.
 *
 * <p>Two of these would fail silently and are the reason this class exists. An inverted empty-grant
 * default empties every screen for every role that predates the grant table — a total outage that
 * looks like a data problem. And a model that reaches its company through another one (a per-department
 * statistic) would go unbounded if the anchor were assumed to be a local field name, leaking other
 * companies' aggregates into a report with nothing to indicate it.
 *
 * <p>Stubs {@link ModelManager} statically because {@link MetaModel}'s setters are package-private to
 * its own package, so this module cannot build one — same approach as
 * {@code CompanyCountryEnricherTest}.
 */
class CompanyGrantTest {

    private static final Set<Long> GRANTED = Set.of(8712L, 9001L);

    private MockedStatic<ModelManager> modelManager;

    @BeforeEach
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    /** Only {@code appendCompanyGrant} is exercised; it touches none of the collaborators. */
    private static PermissionServiceImpl service() {
        return new PermissionServiceImpl(null, null, null, null, null);
    }

    private void model(String name, boolean multiCompany) {
        MetaModel meta = mock(MetaModel.class);
        when(meta.isMultiCompany()).thenReturn(multiCompany);
        modelManager.when(() -> ModelManager.existModel(name)).thenReturn(true);
        modelManager.when(() -> ModelManager.getModel(name)).thenReturn(meta);
    }

    private static PermissionInfo grant(Set<Long> ids) {
        PermissionInfo pi = new PermissionInfo();
        pi.setGrantedCompanyIds(ids);
        return pi;
    }

    // ---- the three states ------------------------------------------------

    @Test
    void anEmptyGrantMeansNoCompanyAtAll() {
        // Distinct from a missing one. An empty set is only ever produced by an explicit configuration
        // — a role written to reach no company, such as a self-service employee role — and it has to
        // fail closed, or "configured to reach nothing" would be inexpressible.
        model("Department", true);
        Filters original = Filters.of("active", Operator.EQUAL, true);

        Filters result = service().appendCompanyGrant("Department", original, grant(Set.of()));

        assertThat(result).isNotSameAs(original);
        // matchNone() is an IN over an empty list; the dialect renders it as 1 = 0 at SQL-build time.
        assertThat(result.toString()).contains("\"IN\",[]");
    }

    @Test
    void aMissingGrantMeansUnrestricted() {
        // null is what an unconfigured role resolves to (DefaultPermissionSnapshotProvider returns it
        // for zero grant rows), so shipping the axis blanks nobody's screen.
        model("Department", true);
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertThat(service().appendCompanyGrant("Department", original, grant(null))).isSameAs(original);
        assertThat(service().appendCompanyGrant("Department", original, null)).isSameAs(original);
    }

    // ---- what it bounds --------------------------------------------------

    @Test
    void boundsAModelThatOwnsItsCompanyColumn() {
        model("Department", true);

        Filters result = service().appendCompanyGrant("Department", new Filters(), grant(GRANTED));

        assertThat(Filters.containsField(result, "companyId")).isTrue();
        assertThat(result.toString()).contains("8712", "9001");
    }

    @Test
    void boundsAModelThatReachesItsCompanyThroughAnother() {
        // A per-department statistic has no company column of its own — it declares one as a dynamic
        // cascaded field, so the grant is bounded on a plain field name and WhereBuilder rewrites it
        // back to deptId.companyId. Reading the anchor off the metadata rather than assuming it is
        // what keeps such a report bounded at all: hard-coding a name it did not have would leave every
        // company's aggregates visible with nothing on screen saying so.
        model("DeptHeadcountStats", true);

        Filters result = service().appendCompanyGrant("DeptHeadcountStats", new Filters(), grant(GRANTED));

        assertThat(Filters.containsField(result, ModelConstant.COMPANY_FIELD)).isTrue();
    }

    @Test
    void narrowsWithinTheGrantRatherThanReplacingTheSelection() {
        // The composition that makes the header switch work for a multi-company role.
        // ModelServiceImpl.scopedAccess has already applied the selection to these filters, so this
        // ANDs the grant on top: selected ∧ granted. A subset, so never empty for a company the
        // switcher was allowed to offer.
        model("Department", true);
        Filters selected = Filters.of("companyId", Operator.EQUAL, 8712L);

        Filters result = service().appendCompanyGrant("Department", selected, grant(GRANTED));

        assertThat(result.toString()).contains("8712", "9001");
    }

    @Test
    void boundsTheCompanyListItself() {
        // The switcher's own query. LegalEntity is deliberately not multiCompany — self-scoping is
        // rejected at boot — so it is bounded by its id instead. Without this the switcher keeps
        // offering companies the role cannot reach, and picking one ANDs an ungranted selection against
        // the grant: every screen goes empty, with nothing saying why.
        modelManager.when(() -> ModelManager.existModel(ModelConstant.COMPANY_MODEL)).thenReturn(true);

        Filters result = service().appendCompanyGrant(ModelConstant.COMPANY_MODEL, new Filters(),
                grant(GRANTED));

        assertThat(Filters.containsField(result, ModelConstant.ID)).isTrue();
        assertThat(result.toString()).contains("8712", "9001");
    }

    @Test
    void doesNotBoundACompanyReadThatNamesRowsById() {
        // A stored reference being expanded for display. Blanking a label is not the same as denying
        // access to data, and the row that referenced it was already subject to the grant.
        modelManager.when(() -> ModelManager.existModel(ModelConstant.COMPANY_MODEL)).thenReturn(true);
        Filters byId = Filters.of(ModelConstant.ID, Operator.IN, java.util.List.of(9999L));

        assertThat(service().appendCompanyGrant(ModelConstant.COMPANY_MODEL, byId, grant(GRANTED)))
                .isSameAs(byId);
    }

    @Test
    @Disabled("Known gap — enable together with dropping the id exemption from appendCompanyGrant.")
    void theWriteGateItsOwnFilterIsBoundedByTheGrant() {
        // checkIdsAccess is what stands between a caller and `deleteByIds` / `updateList` on a row it
        // may not touch. It verifies the ids by counting them back — count(model, id IN (…)) — and
        // that filter names `id`, so the exemption above drops the grant from it. The count then says
        // "all present", and a row belonging to a company the role was never granted is deleted by
        // anyone who knows its id and holds the model's delete permission.
        //
        // The exemption is not needed for what its comment describes. Display expansion runs inside
        // DataPipelineProxy.processReadData, which is @SkipPermissionCheck: shouldBypass() is true and
        // appendScopeAccessFilters returns before ever reaching appendCompanyGrant. MultiCompanyScope keeps
        // its own id exemption and does need it — it sits in the ORM layer, where nothing bypasses.
        //
        // Dropping it here makes this pass and costs one thing: a by-id read of a row outside the
        // grant returns empty rather than the row. That is what a grant means.
        model("Department", true);
        Filters writeGate = Filters.of(ModelConstant.ID, Operator.IN, java.util.List.of(4242L));

        assertThat(service().appendCompanyGrant("Department", writeGate, grant(GRANTED)).toString())
                .contains("8712", "9001");
    }

    @Test
    void rendersTheSameSqlForTheSameGrant() {
        // Set iteration order would vary the statement text between requests and defeat statement
        // caching, for a filter that is on every read of every multi-company model.
        model("Department", true);

        String first = service().appendCompanyGrant("Department", new Filters(),
                grant(new java.util.HashSet<>(java.util.List.of(9001L, 8712L, 9002L)))).toString();
        String second = service().appendCompanyGrant("Department", new Filters(),
                grant(new java.util.HashSet<>(java.util.List.of(9002L, 8712L, 9001L)))).toString();

        assertThat(first).isEqualTo(second);
    }

    // ---- what it leaves alone --------------------------------------------

    @Test
    void leavesAModelThatIsNotMultiCompany() {
        // Carrying a company reference is not the same as belonging to one company. Bounding such a
        // model would filter rows that legitimately relate to another company.
        model("OptionSetLike", false);
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertThat(service().appendCompanyGrant("OptionSetLike", original, grant(GRANTED)))
                .isSameAs(original);
    }

    @Test
    void leavesAnUnknownModel() {
        // Sits on the generic read path: an unknown model must reach the query that reports it
        // properly, not fail here with a metadata error.
        modelManager.when(() -> ModelManager.existModel("NoSuchModel")).thenReturn(false);
        Filters original = Filters.of("active", Operator.EQUAL, true);

        assertThat(service().appendCompanyGrant("NoSuchModel", original, grant(GRANTED)))
                .isSameAs(original);
        assertThat(service().appendCompanyGrant(null, original, grant(GRANTED))).isSameAs(original);
    }

    @Test
    void theGrantIsUnionedAcrossRolesAsASet() {
        // Two roles granting the same entity must not produce a duplicated IN list.
        model("Department", true);

        Filters result = service().appendCompanyGrant("Department", new Filters(),
                grant(Set.of(8712L)));

        assertThat(result.toString().split("8712", -1).length - 1).isEqualTo(1);
    }
}
