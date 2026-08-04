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
 * <p><b>Why it is not the same thing as the permission layer's LEGAL_ENTITY scope.</b> That scope
 * answers "which company's rows is this user allowed to see", anchored on the caller's own employee
 * record, and it is a grant. This answers "which company is the user looking at right now", anchored
 * on the header selection, and it is a view.
 *
 * <p>They cannot contradict each other, and the mechanism that guarantees it is the
 * already-constrained check below: a LEGAL_ENTITY rule compiles to a condition on this same field, so
 * by the time this runs the field is taken and the selection is not applied on top. The grant wins,
 * which is the only safe resolution — AND-ing a granted company with a different selected one yields
 * nothing, and a user whose grant pins one company would see empty lists whenever the header sat
 * elsewhere. The selection can therefore never widen what the grant allows, nor empty it.
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
        Long selectedCompanyId = ContextHolder.getContext().getSelectedCompanyId();
        if (selectedCompanyId == null) {
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
        return Filters.and(filters, Filters.of(companyField, Operator.EQUAL, EnvConstant.SELECTED_COMP_ID));
    }
}
