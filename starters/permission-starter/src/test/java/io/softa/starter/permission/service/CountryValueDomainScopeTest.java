package io.softa.starter.permission.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.base.enums.Operator;
import io.softa.starter.permission.scope.ScopeApplicabilityResolver;
import io.softa.starter.permission.scope.ScopeRuleCompiler;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.spi.ScopeType;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A table whose rows ARE the options of a field is readable without a grant.
 *
 * <p>The case that produced this: {@code IdType} is referenced only from {@code EmployeeProfile}, and
 * {@code EmployeeProfile} is reached through {@code Employee} rather than granted in its own right — so
 * {@code findReferencer}, which scans the granted models, found nothing and the read fell to
 * "unreachable" → zero rows. On screen: an ID Type dropdown reading "No options available" with the
 * rows present in the table and the country filter correct.
 *
 * <p>What decides it is {@code multiCountry} + not multi-tenant: a declaration about what the data IS,
 * paired with the conjunct that keeps business data out. The cases below are mostly about what that must
 * NOT let through, because the whole argument for this predicate over the broader one that was measured
 * (code-as-id, 24 models) is that it opens seven and nothing else — so a shared catalogue that is not
 * country-partitioned, a tenant-owned table, and an anchored model must each still fail closed.
 */
class CountryValueDomainScopeTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 42L;

    /** What an anchorless model resolves to — the universal types, and only those. */
    private static final Set<ScopeType> UNIVERSAL_ONLY =
            Set.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.CREATED_BY_SELF);

    private MockedStatic<ModelManager> modelManager;
    private ScopeApplicabilityResolver applicability;
    private PermissionServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);
        applicability = mock(ScopeApplicabilityResolver.class);

        PermissionInfo pi = new PermissionInfo();
        // The caller holds Employee and nothing else — in particular no grant on any option source.
        pi.setModelScopeMap(Map.of("Employee", List.of(rule(ScopeType.ALL))));
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenReturn(pi);

        service = new PermissionServiceImpl(provider, null, null, mock(ModelService.class), applicability);

        // Nothing the caller holds points at the models under test — the state that used to deny them.
        modelManager.when(() -> ModelManager.existModel("Employee")).thenReturn(true);
        modelManager.when(() -> ModelManager.getModelFields("Employee")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    // ── readable ──────────────────────────────────────────────────────────────────────────────────

    @Test
    void aCountryValueDomainIsReadableWithoutAGrant() {
        // IdType: rows partitioned by country, shared across tenants, no anchor of its own.
        declare("IdType", true, false, UNIVERSAL_ONLY);

        assertThat(scopeOf("IdType")).isEqualTo(new Filters());
    }

    // ── what it must not let through ──────────────────────────────────────────────────────────────

    @Test
    void aTenantOwnedTableIsNotAValueDomain() {
        // multiCountry is not on its own enough: a country-partitioned table that belongs to a tenant
        // is still that tenant's data, and reading it unscoped would cross the boundary the tenant
        // column draws. This is the conjunct that keeps business data out.
        declare("TenantOptionItem", true, true, UNIVERSAL_ONLY);

        assertThat(scopeOf("TenantOptionItem")).isEqualTo(matchNoneAnded());
    }

    @Test
    void aSharedCatalogueThatIsNotCountryPartitionedStaysClosed() {
        // The case that decided the predicate. Navigation is shared, code-as-id and anchorless, so a
        // broader rule keyed on the key strategy would have opened it along with twelve others this
        // bug never needed. Nothing about an empty ID Type dropdown argues for exposing the page
        // catalogue — so it stays closed, and this is what says so.
        declare("Navigation", false, false, UNIVERSAL_ONLY);

        assertThat(scopeOf("Navigation")).isEqualTo(matchNoneAnded());
    }

    @Test
    void anAnchoredModelNeverQualifiesHoweverItIsFlagged() {
        // The anchor check runs first, so a country-partitioned model that IS business data cannot
        // reach the branch at all. Asserted rather than assumed: it is what keeps the anchor list in
        // one place (the DataScopeType registry) instead of being restated in the predicate.
        declare("EmpDocument", true, false,
                Set.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.CREATED_BY_SELF, ScopeType.DEPT_SUBTREE));

        assertThat(scopeOf("EmpDocument")).isEqualTo(matchNoneAnded());
    }

    @Test
    void anUnknownModelIsNotAssumedToBeAnOptionSource() {
        when(applicability.applicableFor("Ghost")).thenReturn(UNIVERSAL_ONLY);
        modelManager.when(() -> ModelManager.getModel("Ghost")).thenReturn(null);

        assertThat(scopeOf("Ghost")).isEqualTo(matchNoneAnded());
    }

    // ── an explicit configuration still wins ──────────────────────────────────────────────────────

    @Test
    void anAdministratorsOwnRuleOnAnOptionSourceIsHonoured() {
        // The branch sits in the no-grant fallback, so a role deliberately narrowed to part of a
        // option source keeps that narrowing — being readable by default must not mean unrestricted.
        declare("IdType", true, false, UNIVERSAL_ONLY);
        PermissionInfo pi = new PermissionInfo();
        pi.setModelScopeMap(Map.of("IdType", List.of(rule(ScopeType.CREATED_BY_SELF))));
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenReturn(pi);
        ScopeRuleCompiler compiler = mock(ScopeRuleCompiler.class);
        when(compiler.compile(anyList(), eq("IdType"))).thenReturn(Filters.of("id", Operator.EQUAL, "SG_NRIC"));
        service = new PermissionServiceImpl(provider, compiler, null, mock(ModelService.class), applicability);

        assertThat(scopeOf("IdType").toString()).contains("SG_NRIC");
        verify(compiler).compile(anyList(), eq("IdType"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /** Declare a model's country partitioning, tenancy and which scope types apply to it. */
    private void declare(String model, boolean multiCountry, boolean multiTenant, Set<ScopeType> applicable) {
        when(applicability.applicableFor(model)).thenReturn(applicable);
        // MetaModel's setters are package-private — a mock is the only way to shape one from here.
        MetaModel meta = mock(MetaModel.class);
        when(meta.isMultiCountry()).thenReturn(multiCountry);
        when(meta.isMultiTenant()).thenReturn(multiTenant);
        modelManager.when(() -> ModelManager.getModel(model)).thenReturn(meta);
    }

    private static ScopeRule rule(ScopeType type) {
        ScopeRule r = new ScopeRule();
        r.setScopeType(type);
        return r;
    }

    private Filters scopeOf(String model) {
        Context ctx = new Context();
        ctx.setTenantId(TENANT);
        ctx.setUserId(USER);
        return ContextHolder.callWith(ctx, () -> service.appendScopeAccessFilters(model, new Filters()));
    }

    /** What {@code combineAnd(new Filters(), matchNone())} produces. */
    private Filters matchNoneAnded() {
        declare("__denied__", false, false,
                Set.of(ScopeType.ALL, ScopeType.DEPT_SUBTREE));
        return scopeOf("__denied__");
    }

}
