package io.softa.starter.permission.service;

import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.scope.ModelDefaultScopeRegistry;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a model gets when nobody granted it and it declares a fallback of its own.
 *
 * <p>Without a declaration, an anchorless model that nothing the caller holds points at resolves to
 * no rows. That is right for business data somebody forgot to grant and wrong for a table with no
 * owner — a template catalogue, a server configuration — which then comes back empty with nothing on
 * screen saying why: the menu grant opened the page, the row scope emptied it, and no layer in
 * between reports the disagreement.
 *
 * <p>The declaration is read through the same compiler a configured rule goes through, so the two
 * cannot drift: what an administrator gets by picking {@code CREATED_BY_SELF} in the wizard is what
 * a model gets by declaring it. The cases below pin the branch's edges — it must not fire on a model
 * that has an anchor of its own, and it must never overrule a rule somebody actually wrote.
 */
class DefaultScopeFallbackTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 42L;

    /** What an anchorless model resolves to — the universal types, and only those. */
    private static final Set<ScopeType> UNIVERSAL_ONLY =
            Set.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.CREATED_BY_SELF);

    /** A stand-in for whatever CREATED_BY_SELF compiles to; its shape is the compiler's business. */
    private static final Filters OWN_ROWS = Filters.of("createdId", Operator.EQUAL, "USER_ID");

    private MockedStatic<ModelManager> modelManager;
    private ScopeApplicabilityResolver applicability;
    private ScopeRuleCompiler compiler;
    private ModelDefaultScopeRegistry defaultScopes;
    private PermissionServiceImpl service;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);
        applicability = mock(ScopeApplicabilityResolver.class);
        compiler = mock(ScopeRuleCompiler.class);
        defaultScopes = mock(ModelDefaultScopeRegistry.class);

        PermissionInfo pi = new PermissionInfo();
        // The caller holds Employee and nothing else — nothing points at the models under test.
        pi.setModelScopeMap(Map.of("Employee", List.of(rule(ScopeType.ALL))));
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenReturn(pi);

        service = new PermissionServiceImpl(provider, compiler, null, mock(ModelService.class),
                applicability, () -> null, () -> null, () -> defaultScopes);

        modelManager.when(() -> ModelManager.existModel("Employee")).thenReturn(true);
        modelManager.when(() -> ModelManager.getModelFields("Employee")).thenReturn(List.of());
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    @Test
    void aModelDeclaringAllIsReadWithoutRestriction() {
        // The compiler answers ALL with no filter at all, which the branch has to pass through as-is
        // rather than treating as "nothing to apply" — the two look the same and mean the opposite.
        declare("ImportTemplate", ScopeType.ALL, UNIVERSAL_ONLY);
        when(compiler.compile(anyList(), eq("ImportTemplate"))).thenReturn(null);

        assertThat(scopeOf("ImportTemplate")).isEqualTo(new Filters());
    }

    @Test
    void aModelDeclaringCreatedBySelfIsNarrowedToTheCaller() {
        declare("ImportHistory", ScopeType.CREATED_BY_SELF, UNIVERSAL_ONLY);
        when(compiler.compile(anyList(), eq("ImportHistory"))).thenReturn(OWN_ROWS);

        assertThat(scopeOf("ImportHistory")).isNotEqualTo(new Filters());

        // Compiled as the rule it names, through the same path a configured rule takes.
        ArgumentCaptor<List<ScopeRule>> rules = ArgumentCaptor.forClass(List.class);
        verify(compiler).compile(rules.capture(), eq("ImportHistory"));
        assertThat(rules.getValue()).singleElement()
                .extracting(ScopeRule::getScopeType).isEqualTo(ScopeType.CREATED_BY_SELF);
    }

    @Test
    void aModelDeclaringNothingStaysClosed() {
        // The default. Declaring nothing must leave the previous behaviour exactly as it was.
        declare("SomeBusinessTable", null, UNIVERSAL_ONLY);

        assertThat(scopeOf("SomeBusinessTable")).isEqualTo(matchNoneAnded());
        verify(compiler, never()).compile(anyList(), eq("SomeBusinessTable"));
    }

    @Test
    void anAnchoredModelIgnoresItsDeclaration() {
        // Declaring a fallback on a model that can be scoped on its own means one of the two is
        // wrong, and the anchor is the safer reading: a model with a department column is business
        // data, and opening it because somebody mislabelled it would be the worse mistake.
        declare("EmpDocument", ScopeType.ALL,
                Set.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.CREATED_BY_SELF, ScopeType.DEPT_SUBTREE));

        assertThat(scopeOf("EmpDocument")).isEqualTo(matchNoneAnded());
        verify(compiler, never()).compile(anyList(), eq("EmpDocument"));
    }

    @Test
    void anAdministratorsOwnRuleWinsOverTheDeclaration() {
        // The branch lives in the no-grant fallback, so a role that was deliberately narrowed keeps
        // its narrowing. A declared default is what to do in the ABSENCE of a decision, never a
        // ceiling on one.
        declare("ImportHistory", ScopeType.ALL, UNIVERSAL_ONLY);
        PermissionInfo pi = new PermissionInfo();
        pi.setModelScopeMap(Map.of("ImportHistory", List.of(rule(ScopeType.CREATED_BY_SELF))));
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenReturn(pi);
        when(compiler.compile(anyList(), eq("ImportHistory"))).thenReturn(OWN_ROWS);
        service = new PermissionServiceImpl(provider, compiler, null, mock(ModelService.class),
                applicability, () -> null, () -> null, () -> defaultScopes);

        assertThat(scopeOf("ImportHistory")).isNotEqualTo(new Filters());
        // Compiled from the role's rule; the model's own declaration never reached the compiler.
        ArgumentCaptor<List<ScopeRule>> rules = ArgumentCaptor.forClass(List.class);
        verify(compiler).compile(rules.capture(), eq("ImportHistory"));
        assertThat(rules.getValue()).singleElement()
                .extracting(ScopeRule::getScopeType).isEqualTo(ScopeType.CREATED_BY_SELF);
    }

    @Test
    void aRowTheRegistryCouldNotUseLeavesTheModelClosed() {
        // A row naming no ScopeType is dropped by the registry, which answers null — the same
        // answer as "no row at all". Both leave the model on the fail-closed path, which is the
        // safe reading of a value nobody can make sense of. The boot report names such rows.
        declare("Typo", null, UNIVERSAL_ONLY);

        assertThat(scopeOf("Typo")).isEqualTo(matchNoneAnded());
        verify(compiler, never()).compile(anyList(), eq("Typo"));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────────────────────

    /** Declare a model's fallback scope and which scope types apply to it. */
    private void declare(String model, ScopeType defaultScope, Set<ScopeType> applicable) {
        when(applicability.applicableFor(model)).thenReturn(applicable);
        when(defaultScopes.scopeFor(model)).thenReturn(defaultScope);
        modelManager.when(() -> ModelManager.existModel(model)).thenReturn(true);
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
        declare("__denied__", null, Set.of(ScopeType.ALL, ScopeType.DEPT_SUBTREE));
        return scopeOf("__denied__");
    }
}
