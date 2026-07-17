package io.softa.starter.permission.scope;

import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.permission.spi.ScopeContributor;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.spi.ScopeType;

/**
 * Compile a list of {@link ScopeRule} (OR-combined) into a {@link Filters}
 * that {@code ScopeFilterAspect} AND-merges into the user's query.
 *
 * <h3>Dispatch model</h3>
 * Each {@link ScopeType} has exactly one {@link ScopeContributor} bean
 * registered against it. The compiler is a pure dispatcher:
 * <ol>
 *   <li>{@link ScopeType#ALL} — handled inline (returns null sentinel
 *       meaning "no scope restriction").</li>
 *   <li>Every other type — looked up in the {@code contributorsByType}
 *       map and the rule is forwarded to the contributor's
 *       {@link ScopeContributor#compile} method.</li>
 * </ol>
 *
 * <h3>Why this design</h3>
 * The framework's three generic scope types (ALL, CUSTOM,
 * CREATED_BY_SELF) ship with user-starter. Domain-specific types
 * (SELF, DIRECT_REPORTS, DEPT_SUBTREE, MANAGED_DEPARTMENTS, LEGAL_ENTITY)
 * live in the consuming business module as their own
 * {@link ScopeContributor} beans. The compiler doesn't import business
 * concepts; it just dispatches by ScopeType.
 *
 * <h3>Fail-closed semantics</h3>
 * Contributors signal "this rule degraded" by returning
 * {@code new Filters()} (EMPTY-typed). The OR-merge in
 * {@link #compile(java.util.List, PermissionInfo, String)} skips those. If
 * every rule degrades (or the rule list is empty), {@code compile} emits a
 * "match no rows" filter ({@link #matchNone()}) — an ordinary empty-tuple
 * {@code IN} leaf that renders {@code WHERE 1=0}. Being a normal leaf it
 * AND-combines with the caller's query like any predicate, so the query runs
 * and the DB returns zero rows. This closes the C1 gap where
 * {@link Filters#EMPTY} was silently dropped by {@code combine}/{@code WhereBuilder},
 * leaking the whole table.
 */
@Slf4j
@Component
public final class ScopeRuleCompiler {

    private final ScopeApplicabilityResolver applicability;
    private final IdentityScopeCompiler identityCompiler;
    private final Map<ScopeType, ScopeContributor> contributorsByType;

    /**
     * Spring constructor — injects every {@link ScopeContributor} bean the
     * application context knows about, plus the data-driven
     * {@link IdentityScopeCompiler}. Duplicate contributors for one type throw.
     *
     * <p>Dispatch precedence per type: ALL (inline) → a registered
     * {@link ScopeContributor} → the {@link IdentityScopeCompiler} data path
     * (SELF / DIRECT_REPORTS / CREATED_BY_SELF / LEGAL_ENTITY, whose
     * {@code DataScopeType} rows declare a {@code filter} template) → fail-closed.
     * A type with none of these degrades to empty at runtime (logged at debug in
     * {@link #compileOne}); we don't warn at boot because identity handling is
     * data-driven and unknown until the seed is read.
     */
    public ScopeRuleCompiler(
            ScopeApplicabilityResolver applicability,
            IdentityScopeCompiler identityCompiler,
            List<ScopeContributor> contributors) {
        this.applicability = applicability;
        this.identityCompiler = identityCompiler;
        Map<ScopeType, ScopeContributor> map = new EnumMap<>(ScopeType.class);
        for (ScopeContributor c : contributors) {
            ScopeContributor prior = map.putIfAbsent(c.scopeType(), c);
            if (prior != null && prior != c) {
                throw new IllegalStateException(
                        "Multiple ScopeContributor beans for " + c.scopeType()
                                + ": " + prior.getClass().getName() + " and " + c.getClass().getName());
            }
        }
        this.contributorsByType = Map.copyOf(map);
    }

    /**
     * Compile the rules into one OR-combined Filters. Returns:
     * <ul>
     *   <li>{@code null} — any rule is ALL → no scope filter (caller appends nothing).</li>
     *   <li>{@link #matchNone()} — rules list is empty OR every rule degraded:
     *       a {@code 1=0} leaf so the query returns zero rows.</li>
     *   <li>otherwise — OR-merged Filters expression.</li>
     * </ul>
     */
    public Filters compile(List<ScopeRule> rules, String modelName) {
        if (rules == null || rules.isEmpty()) return matchNone();
        for (ScopeRule r : rules) {
            if (r.getScopeType() == ScopeType.ALL) return null;
        }

        Filters out = null;
        for (ScopeRule rule : rules) {
            Filters one = compileOne(rule, modelName);
            if (one == null) continue;
            if (Filters.isEmpty(one)) continue;
            out = (out == null) ? one : out.or(one);
        }
        return out == null ? matchNone() : out;
    }

    private Filters compileOne(ScopeRule rule, String modelName) {
        if (rule == null || rule.getScopeType() == null) return emptyFilter();
        ScopeType type = rule.getScopeType();
        if (type == ScopeType.ALL) return null;
        // Fail-fast on rules whose scope doesn't apply to this model (the
        // anchor column was removed or the nav points at the wrong model).
        if (modelName != null && !applicability.applicableFor(modelName).contains(type)) {
            return emptyFilter();
        }
        try {
            ScopeContributor contributor = contributorsByType.get(type);
            if (contributor != null) {
                return contributor.compile(rule, modelName);
            }
            // No code contributor — try the data-driven identity path
            // (SELF / DIRECT_REPORTS / CREATED_BY_SELF / LEGAL_ENTITY compile
            // from their DataScopeType rows). Returns null when the type isn't a
            // data-driven identity type, i.e. genuinely unhandled.
            Filters identity = identityCompiler.compile(type, rule, modelName);
            if (identity != null) {
                return identity;
            }
            log.debug("No ScopeContributor or identity spec for {}, degrading to empty", type);
            return emptyFilter();
        } catch (IllegalStateException ise) {
            // Contributors throw IllegalStateException for config errors
            // (e.g. DepartmentCascadePathResolver returning empty for a model
            // that ApplicabilityResolver accepted). Propagate so it
            // surfaces at the first request.
            throw ise;
        } catch (Throwable t) {
            log.warn("Scope compile for {} threw; degrading to empty", type, t);
            return emptyFilter();
        }
    }

    /**
     * Internal degraded-rule marker (EMPTY-typed) — signals "this single rule
     * degraded" to the OR-merge loop in {@link #compile}, which skips it via
     * {@link Filters#isEmpty}. NOT fail-closed on its own — if every rule
     * degrades, {@code compile}'s exit converts to {@link #matchNone()}.
     */
    private static Filters emptyFilter() {
        return new Filters();
    }

    /**
     * Fail-closed "match no rows" filter — an ordinary empty-tuple {@code IN}
     * leaf that renders {@code WHERE 1=0}. A normal leaf (not a special type),
     * so it AND-combines with the caller's filters like any predicate: the
     * query runs and the DB returns zero rows. The {@code id} anchor exists on
     * every model, so this is model-agnostic and joins nothing.
     */
    public static Filters matchNone() {
        return Filters.of(List.of(ModelConstant.ID, ModelConstant.ID), Operator.IN, Collections.emptyList());
    }
}
