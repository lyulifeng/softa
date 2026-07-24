package io.softa.starter.permission.service;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.PermissionService;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.sensitive.SensitiveFieldSetCache;
import io.softa.starter.permission.scope.ScopeApplicabilityResolver;
import io.softa.starter.permission.scope.ScopeRuleCompiler;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import lombok.extern.slf4j.Slf4j;

import java.io.Serializable;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Bridges the framework's {@link PermissionService} contract to the
 * user-starter's runtime permission snapshot.
 *
 * <p>Every call:
 * <ol>
 *   <li>Bypasses when there's no bound {@code ContextHolder} scope, when
 *       {@code Context.skipPermissionCheck=true}, or when {@code userId}
 *       is null (bootstrap / async / cron paths).</li>
 *   <li>Loads {@link PermissionInfo} via
 *       {@link PermissionInfoEnricher#enrich} (request-scoped + Redis
 *       cached — repeat calls in one request are free).</li>
 *   <li>Super-admin short-circuits every check.</li>
 *   <li>Delegates rule → SQL translation to {@link ScopeRuleCompiler}
 *       and field-mask resolution to {@link SensitiveFieldSetCache}.</li>
 * </ol>
 *
 * <p>Row-scope fail-closed: models with no entry in
 * {@code modelScopeMap} read through {@link ScopeRuleCompiler#matchNone()}
 * — an empty-tuple {@code IN} leaf rendering {@code WHERE 1=0}, so reads
 * return zero rows. Cross-model relation expansion (Employee →
 * department.name etc.) bypasses this via
 * {@code Context.skipPermissionCheck=true} set by the JDBC pipeline's
 * {@code RelationExpansions} helper, so display-name expansion still works
 * even when the user has no scope on the related model.
 */
@Slf4j
public class PermissionServiceImpl implements PermissionService {

    // @Lazy breaks the init cycle: PermissionServiceImpl ← ModelServiceImpl
    // ← NavigationModelResolverImpl ← PermissionInfoEnricher ← this. Every
    // dependency here is called per-request, never during Spring's bean
    // wiring phase — deferring resolution to first invocation is safe.
    private final PermissionSnapshotProvider snapshotProvider;
    private final ScopeRuleCompiler scopeCompiler;
    private final SensitiveFieldSetCache sfsCache;
    /** ModelService is called back from {@link #checkIdsAccess} to run a
     *  scope-restricted count on the target ids. Framework's {@code count}
     *  routes back through {@code appendScopeAccessFilters}, so the AND-ed
     *  scope makes any out-of-scope id disappear from the count. */
    private final ModelService<?> modelService;
    /** "Which ScopeTypes apply to a model" — lets us tell a truly anchorless
     *  config/extension model (only ALL applies) from real business data that
     *  merely has no grant yet. */
    private final ScopeApplicabilityResolver applicability;

    public PermissionServiceImpl(
            PermissionSnapshotProvider snapshotProvider,
            ScopeRuleCompiler scopeCompiler,
            SensitiveFieldSetCache sfsCache,
            ModelService<?> modelService,
            ScopeApplicabilityResolver applicability) {
        this.snapshotProvider = snapshotProvider;
        this.scopeCompiler = scopeCompiler;
        this.sfsCache = sfsCache;
        this.modelService = modelService;
        this.applicability = applicability;
    }

    // ─────────────────────── row-scope ───────────────────────

    @Override
    public Filters appendScopeAccessFilters(String model, Filters originalFilters) {
        if (shouldBypass()) return originalFilters;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return originalFilters;
        if (hasExplicitRules(pi, model)) {
            Filters scope = scopeCompiler.compile(rulesFor(pi, model), model);
            if (scope == null) return originalFilters; // ALL rule → no restriction
            return combineAnd(originalFilters, scope);
        }
        // No explicit grant. Real business data (has a forward scope anchor)
        // stays fail-closed; only a truly anchorless config/extension model gets
        // the metadata-derived follow-parent / shared treatment below. (Cross-
        // model display expansion still bypasses everything via skipPermissionCheck.)
        if (hasForwardAnchor(model)) {
            return combineAnd(originalFilters, ScopeRuleCompiler.matchNone());
        }
        Referencer ref = findReferencer(model, pi);
        if (ref == null) {
            return combineAnd(originalFilters, ScopeRuleCompiler.matchNone()); // unreachable
        }
        if (!ref.owned()) {
            return originalFilters; // shared reference/config (ManyToOne target) → readable
        }
        // ONE_TO_ONE owned child → follow the owner: visible ids = the FK values
        // of in-scope owner rows. getRelatedIds re-enters scope for the owner, so
        // the owner's own row-scope is applied (parent strict ⇒ child strict).
        List<Serializable> visible =
                modelService.getRelatedIds(ref.parentModel(), new Filters(), ref.fkField());
        if (visible.isEmpty()) {
            return combineAnd(originalFilters, ScopeRuleCompiler.matchNone());
        }
        return combineAnd(originalFilters, Filters.of(ModelConstant.ID, Operator.IN, visible));
    }

    // ─────────────────────── field mask ───────────────────────

    @Override
    public Collection<String> filterReadableFields(String model, Collection<String> requested, AccessType accessType) {
        if (requested == null || requested.isEmpty() || shouldBypass()) return requested;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return requested;
        Set<String> blocked = blockedFields(pi, model);
        if (blocked.isEmpty()) return requested;
        List<String> out = new ArrayList<>(requested.size());
        for (String f : requested) if (!blocked.contains(f)) out.add(f);
        return out;
    }

    @Override
    public <T> T maskResponseValue(String model, T value, AccessType accessType) {
        if (value == null || shouldBypass()) return value;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return value;
        Set<String> blocked = blockedFields(pi, model);
        if (blocked.isEmpty()) return value;
        maskInPlace(value, blocked);
        return value;
    }

    private static void maskInPlace(Object value, Set<String> blocked) {
        if (value == null) return;
        if (value instanceof Optional<?> opt) {
            opt.ifPresent(v -> maskInPlace(v, blocked));
            return;
        }
        if (value instanceof Page<?> page) {
            maskInPlace(page.getRows(), blocked);
            return;
        }
        if (value instanceof Collection<?> coll) {
            for (Object el : coll) maskInPlace(el, blocked);
            return;
        }
        if (value instanceof Map<?, ?> map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> row = (Map<String, Object>) map;
            for (String f : blocked) {
                if (row.containsKey(f)) row.put(f, null);
            }
        }
        // POJO / primitive → nothing to do here.
    }

    // ─────────────────────── write guard ───────────────────────

    @Override
    public void checkModelFieldsAccess(String model, Collection<String> fields, AccessType accessType) {
        if (fields == null || fields.isEmpty() || shouldBypass()) return;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return;
        Set<String> blocked = blockedFields(pi, model);
        if (blocked.isEmpty()) return;
        for (String f : fields) {
            if (blocked.contains(f)) {
                throw new PermissionException(
                        "No " + accessType + " permission for field " + model + "." + f);
            }
        }
    }

    @Override
    public void checkIdsFieldsAccess(String model,
                                     Collection<? extends Serializable> ids,
                                     Set<String> fields,
                                     AccessType accessType) {
        checkModelFieldsAccess(model, fields, accessType);
        checkIdsAccess(model, ids, accessType);
    }

    @Override
    public void checkWritePayload(String model, Map<String, Object> payload) {
        if (payload == null || payload.isEmpty() || shouldBypass()) return;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return;
        Set<String> blocked = blockedFields(pi, model);
        if (blocked.isEmpty()) return;
        for (String f : payload.keySet()) {
            if (blocked.contains(f)) {
                throw new PermissionException(
                        "No write permission for field " + model + "." + f);
            }
        }
    }

    // ─────────────────────── model / id / route access ───────────────────────

    @Override
    public void checkModelAccess(String model, AccessType accessType) {
        // Model-level access is enforced by the endpoint gate
        // (PermissionInterceptor) before the request reaches ModelService;
        // duplicating it here would only fire on internal calls where the
        // caller already established authority.
    }

    @Override
    public void checkModelCascadeFieldsAccess(String model,
                                              Map<String, Set<String>> accessModelFields,
                                              AccessType accessType) {
        // Per-model field access on cascade reads is enforced by
        // checkModelFieldsAccess at each JDBC-pipeline sub-read; the
        // aggregate check would duplicate that work.
    }

    @Override
    public void checkIdAccess(String model, Serializable id, AccessType accessType) {
        if (id == null) return;
        checkIdsAccess(model, List.of(id), accessType);
    }

    /**
     * Enforce that every id is within the caller's row-scope.
     *
     * <p>Uses {@link io.softa.framework.orm.service.ModelService#count} which
     * routes back through {@link #appendScopeAccessFilters} — the AND-ed
     * scope makes any out-of-scope id disappear from the count. When the
     * scope-restricted count differs from the caller's id list size, at
     * least one id is either outside the scope OR non-existent; both cases
     * are rejected without distinguishing (to avoid an info-leak channel).
     *
     * <p>Guards the direct-id write paths ({@code deleteByIds} /
     * {@code updateList}) — filter-based writes ({@code updateByFilter} /
     * {@code deleteByFilters}) already flow through {@code getIds} which
     * has scope applied.
     */
    @Override
    public void checkIdsAccess(String model,
                               Collection<? extends Serializable> ids,
                               AccessType accessType) {
        if (ids == null || ids.isEmpty() || shouldBypass()) return;
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return;
        List<Serializable> idList = new ArrayList<>(ids);

        // Anchorless config/extension model with no explicit grant carries no
        // scope anchor of its own — verify it via metadata (follow the owner /
        // allow shared config) instead of fail-closing to zero rows.
        if (!hasExplicitRules(pi, model) && !hasForwardAnchor(model)) {
            Referencer ref = findReferencer(model, pi);
            if (ref == null) {
                throw new PermissionException(
                        "Some " + model + " ids are outside your " + accessType + " scope");
            }
            if (ref.owned()) {
                // ONE_TO_ONE owned child → verify through the owner (forward-
                // scopable): "are these ids all referenced by an owner row within
                // my scope?" count() re-enters scope on the owner. Bounded by ids.
                long ownedInScope = modelService.count(ref.parentModel(),
                        Filters.of(ref.fkField(), Operator.IN, idList));
                if (ownedInScope != idList.stream().distinct().count()) {
                    throw new PermissionException(
                            "Some " + model + " ids are outside your " + accessType + " scope");
                }
            }
            // shared reference/config (ManyToOne target) → readable, nothing to check
            return;
        }

        long visible = modelService.count(model, Filters.of(ModelConstant.ID, Operator.IN, idList));
        if (visible != idList.size()) {
            throw new PermissionException(
                    "Some " + model + " ids are outside your " + accessType + " scope");
        }
    }

    @Override
    public void checkRouteAccess(String route) {
        // Navigation visibility is enforced by the endpoint gate; the frontend
        // hydrates the sidebar via /me endpoints that already reflect the
        // user's visible nav set.
    }

    @Override
    public Set<String> getUserBlockedModelFields(String model, AccessType accessType) {
        if (shouldBypass()) return Set.of();
        PermissionInfo pi = currentPi();
        if (PermissionInfo.isAdmin(pi)) return Set.of();
        return blockedFields(pi, model);
    }

    // ─────────────────────── helpers ───────────────────────

    private static boolean shouldBypass() {
        if (!ContextHolder.existContext()) return true;
        Context ctx = ContextHolder.getContext();
        return ctx.isSkipPermissionCheck() || ctx.getUserId() == null;
    }

    private PermissionInfo currentPi() {
        Context ctx = ContextHolder.getContext();
        return snapshotProvider.get(ctx.getTenantId(), ctx.getUserId());
    }

    private static List<ScopeRule> rulesFor(PermissionInfo pi, String model) {
        if (pi == null || pi.getModelScopeMap() == null) return null;
        return pi.getModelScopeMap().get(model);
    }

    private Set<String> blockedFields(PermissionInfo pi, String model) {
        if (pi == null) return Set.of();
        Set<String> granted = pi.getModelSensitiveFieldSetsMap() == null
                ? Set.of()
                : pi.getModelSensitiveFieldSetsMap().getOrDefault(model, Set.of());
        return sfsCache.computeForbiddenFields(model, granted);
    }

    private static Filters combineAnd(Filters original, Filters scope) {
        if (original == null || Filters.isEmpty(original)) return scope;
        return Filters.and(original, scope);
    }

    // ───────── metadata-derived scope for anchorless related models ─────────

    private boolean hasExplicitRules(PermissionInfo pi, String model) {
        List<ScopeRule> r = rulesFor(pi, model);
        return r != null && !r.isEmpty();
    }

    /** A model has a forward scope anchor when some ScopeType beyond ALL applies
     *  (a dept / employee / … field the contributors can filter on). A model
     *  where only ALL applies is structurally unscopable on its own. */
    private boolean hasForwardAnchor(String model) {
        return applicability.applicableFor(model).size() > 1;
    }

    /**
     * How an anchorless model is reachable from the caller's GRANTED models,
     * derived purely from metadata (no hard-coded model names): scan the granted
     * models' TO_ONE fields for one pointing at {@code childModel}.
     * <ul>
     *   <li>a {@code ONE_TO_ONE} owner → follow that owner's row-scope
     *       ({@code owned = true});</li>
     *   <li>else a {@code MANY_TO_ONE} referrer → shared reference/config
     *       ({@code owned = false});</li>
     *   <li>none → {@code null} (not reachable from any grant → stays fail-closed).</li>
     * </ul>
     */
    private Referencer findReferencer(String childModel, PermissionInfo pi) {
        if (pi.getModelScopeMap() == null) return null;
        Referencer shared = null;
        for (String granted : pi.getModelScopeMap().keySet()) {
            if (!ModelManager.existModel(granted)) continue;
            for (MetaField f : ModelManager.getModelFields(granted)) {
                if (!childModel.equals(f.getRelatedModel())) continue;
                if (f.getFieldType() == FieldType.ONE_TO_ONE) {
                    return new Referencer(granted, f.getFieldName(), true);
                }
                if (f.getFieldType() == FieldType.MANY_TO_ONE && shared == null) {
                    shared = new Referencer(granted, f.getFieldName(), false);
                }
            }
        }
        return shared;
    }

    private record Referencer(String parentModel, String fkField, boolean owned) {}
}
