package io.softa.framework.orm.scope;

import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import lombok.extern.slf4j.Slf4j;

/**
 * Narrows reads of a multi-company model — one whose rows belong to one employing company, see
 * {@code @Model(multiCompany = true)} — to the company selected for the current request.
 *
 * <p>Sibling of {@link MultiCountryScope}, and applied the same way and for the same reason: around
 * {@code PermissionService.appendScopeAccessFilters} rather than inside it. See that class for why
 * the permission filter is the wrong home — in short, it short-circuits for admins, for an
 * {@code ALL} rule, and for a model with no explicit rule, so a condition added inside would not
 * reach much of what this exists for.
 *
 * <p><b>Why it is not the same thing as the permission layer's company grant.</b> The grant answers
 * "which companies is this role allowed to reach at all" and lives on the role
 * ({@code PermissionInfo.grantedCompanyIds}, applied by {@code PermissionServiceImpl.appendCompanyGrant}).
 * This answers "which company is the user looking at right now", anchored on the header selection, and
 * it is a view. A per-holder company scope type once blurred the two — {@code ScopeType.LEGAL_ENTITY},
 * compiling to the caller's <i>own</i> company so one role behaved differently per holder; it is retired.
 *
 * <p><b>The selection never widens the grant, and never empties a screen either.</b> It is fed to the
 * permission call as its input, so whatever this appends is bounded by whatever the grant appends
 * around it — {@code selected ∧ granted}, and the selection is always the subset. That holds because
 * the switcher is populated from the same grant: it offers exactly the companies the role's data scope
 * on the company model holds, so a selection outside it is not reachable through the UI. A crafted one
 * is, and it yields nothing — which is the correct answer, not a bug to work around here. Authorization
 * is the permission layer's, and this class must not be read as a second opinion on it.
 *
 * <p>The already-constrained check below is a separate concern from either: a caller that names the
 * anchor field itself keeps its own condition, which is what lets a form scope its dropdowns by the
 * company picked <i>in the form</i> rather than the one in the header.
 *
 * <p><b>It narrows a choice, never a lookup.</b> Same id-targeted exemption as
 * {@link MultiCountryScope}, for the same reason: {@code XToOneGroupProcessor} expands a stored
 * {@code ManyToOne} by issuing {@code searchList(relatedModel, id IN (…))}, so a row referencing
 * another company's department would render blank rather than showing what it actually points at.
 * A caller choosing among candidates never filters by id, so nothing that should be narrowed escapes.
 *
 * <p>And a <b>default, not a constraint</b>: a caller that already constrains the company keeps its
 * own condition, which is what lets a form scope its dropdowns by the company picked <i>in the
 * form</i> rather than the one in the header. AND-ing would produce two different company ids and
 * always match nothing.
 */
@Slf4j
public final class MultiCompanyScope {

    private MultiCompanyScope() {
    }

    /**
     * Append the per-company condition when {@code modelName} is multi-company and the request
     * carries a selected company.
     *
     * @param modelName model being queried
     * @param filters   filters already assembled (permission scope included)
     * @return filters, narrowed by company where applicable
     */
    public static Filters append(String modelName, Filters filters) {
        // existModel first: getModel throws on an unknown name, and this sits on the generic read
        // path — an unknown model must fall through to the query that will report it properly.
        if (modelName == null || !ModelManager.existModel(modelName)) {
            return filters;
        }
        MetaModel metaModel = ModelManager.getModel(modelName);
        if (!metaModel.isMultiCompany()) {
            return filters;
        }
        // Fixed by convention, asserted at init (ModelManager.validateMultiCompany) — nothing to
        // resolve or look up per model.
        String companyField = ModelConstant.COMPANY_FIELD;
        Long companyId = ContextHolder.getContext().getCompanyId();
        if (companyId == null) {
            // No company selected: anonymous and public endpoints, service-to-service calls, and a
            // tenant that has not created its first company yet. Narrowing to nothing here would
            // empty every list on the way to creating one.
            log.debug("No selected company in context; model {} is not narrowed", modelName);
            return filters;
        }
        if (Filters.containsField(filters, ModelConstant.ID)) {
            // The caller named the rows — a display expansion, a by-id read, a cascade resolving
            // stored values. Narrowing those would hide data that legitimately belongs to another
            // company, which is not the same as data the caller may not see.
            log.debug("Caller targets {} rows by id; not narrowing by company", modelName);
            return filters;
        }
        if (Filters.containsField(filters, companyField)) {
            log.debug("Caller already constrains {}.{}; keeping its condition", modelName, companyField);
            return filters;
        }
        return Filters.and(filters, Filters.of(companyField, Operator.EQUAL, EnvConstant.COMPANY_ID));
    }
}
