package io.softa.starter.permission.spi;

import io.softa.framework.orm.domain.Filters;

/**
 * SPI — plug-in implementation of a single {@link ScopeType}'s row-filter
 * compilation logic. {@code ScopeRuleCompiler} dispatches each rule to the
 * contributor whose {@link #scopeType()} matches the rule's type.
 *
 * <h3>Applicability is data, not code (2026-07-16)</h3>
 * Which models a scope type applies to is declared in the {@code DataScopeType}
 * registry (read by {@code ScopeApplicabilityResolver}), NOT on this SPI. A
 * contributor only (a) names its {@link ScopeType} and (b) compiles a rule into
 * a {@link Filters}. Keep the {@code DataScopeType} row's
 * {@code applicableFields} / {@code identityModel} in sync with the anchor
 * column {@link #compile} actually uses, so the wizard never offers a scope the
 * compiler can't honor.
 *
 * <h3>Why a registry, not a switch</h3>
 * The generic scope types (ALL, CUSTOM, CREATED_BY_SELF) are domain-agnostic —
 * they live in {@code permission-starter}. Domain-specific types (SELF,
 * DIRECT_REPORTS, DEPT_SUBTREE, MANAGED_DEPARTMENTS, LEGAL_ENTITY) carry
 * semantics that depend on the consuming app's business shape. Dispatching
 * through this registry lets the same compiler handle both, without the engine
 * importing business concepts.
 *
 * <h3>How to register</h3>
 * Mark your implementation as {@code @Component}. Spring collects all beans of
 * this type and injects them into {@code ScopeRuleCompiler}. At startup every
 * non-ALL {@link ScopeType} should have exactly one contributor — duplicates are
 * rejected, missing contributors log a warning (and any rule referencing the
 * orphaned type degrades to fail-closed at compile time).
 */
public interface ScopeContributor {

    /**
     * The single {@link ScopeType} this contributor implements. Used as the
     * dispatch key by {@code ScopeRuleCompiler}.
     */
    ScopeType scopeType();

    /**
     * Compile this rule into a {@link Filters} for the queried model.
     * Return {@link Filters#EMPTY} (i.e. {@code new Filters()}) to fail-closed
     * for this rule — the caller still OR-merges with other rules, but this one
     * contributes "no rows" rather than "every row".
     *
     * <p>Contributors that need the caller's identity read it straight from
     * {@code ContextHolder.getContext()} — {@code getUserId()} for the user id,
     * {@code getEmpInfo()} for the per-request HR context (populated by the app's
     * {@code ContextEnricher}). Env placeholders inside a CUSTOM filter are
     * resolved later by {@code FilterUnitParser} at SQL-build time.
     *
     * <p>The {@code modelName} is the queried model in PascalCase.
     */
    Filters compile(ScopeRule rule, String modelName);
}
