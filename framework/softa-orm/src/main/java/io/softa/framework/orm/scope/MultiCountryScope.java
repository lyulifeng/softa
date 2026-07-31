package io.softa.framework.orm.scope;

import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;

/**
 * Narrows reads of a multi-country model — one whose rows are replicated per country, see
 * {@code @Model(multiCountry = true)} — to the country of the company selected for the
 * current request.
 *
 * <p><b>Why this is applied around the permission filter rather than inside it.</b> Per-country
 * partitioning is data correctness, not authorization: a row belonging to another country is not
 * "data you may not see", it is data that does not apply. Folding it into
 * {@code PermissionService.appendScopeAccessFilters} would inherit that method's short-circuits —
 * it returns the caller's filters untouched for a bypassing or admin principal, for an {@code ALL}
 * scope rule, and (the case that matters most here) for a model with no explicit scope rule that
 * resolves to a shared reference/config target. A freshly converted value-domain model is exactly
 * that last case, so a condition added inside would never reach the models this mechanism exists
 * for. Wrapping the call covers every one of those paths.
 *
 * <p><b>It narrows a choice, never a lookup.</b> A caller that already names the rows it wants —
 * any filter mentioning {@code id} — is resolving values it holds, not choosing among candidates,
 * and gets them back untouched. Without this, expanding a stored value for display would break the
 * moment it came from another country: {@code XToOneGroupProcessor} resolves a {@code ManyToOne} by
 * issuing {@code searchList(relatedModel, id IN (…))}, so an employee whose pass type was recorded
 * under a New Zealand company would render blank while the header sits on a Singapore one. Its
 * {@code FilterControl.bypassAll()} does not help — that only waives active-control and
 * soft-delete. The permission filter avoids the same trap by short-circuiting on
 * {@code skipPermissionCheck}, which this deliberately does not do (see above), so the distinction
 * has to be drawn here instead.
 *
 * <p>The narrowing is also a <b>default, not a constraint</b>: a caller that already constrains the
 * country field keeps its own condition. That is what lets a create-employee or transfer form
 * scope its dropdowns by the company picked <i>in the form</i>, which may differ from the one
 * the header is switched to. AND-ing instead would produce {@code country = 'SG' AND country =
 * 'NZ'} — always empty.
 */
@Slf4j
public final class MultiCountryScope {

    private MultiCountryScope() {
    }

    /**
     * Append the per-country condition when {@code modelName} is multi-country and the request
     * carries a selected company country.
     *
     * @param modelName model being queried
     * @param filters   filters already assembled (permission scope included)
     * @return filters, narrowed by country where applicable
     */
    public static Filters append(String modelName, Filters filters) {
        // existModel first: getModel throws on an unknown name, and this sits on the generic
        // read path — an unknown model must fall through to the query that will report it,
        // not fail here with an unrelated error.
        if (modelName == null || !ModelManager.existModel(modelName)) {
            return filters;
        }
        MetaModel metaModel = ModelManager.getModel(modelName);
        if (!metaModel.isMultiCountry()) {
            return filters;
        }
        String countryField = metaModel.getCountryField();
        if (StringUtils.isBlank(countryField)) {
            // ModelManager fail-fasts at init when a multi-country model has no CountryRegion
            // reference, so reaching here means the metadata was built by some other path.
            log.warn("Model {} is multi-country but no country field was resolved; "
                    + "skipping the per-country narrowing", modelName);
            return filters;
        }
        if (StringUtils.isBlank(ContextHolder.getContext().getSelectedCompanyCountry())) {
            // No company selected: anonymous/public endpoints and service-to-service calls
            // build a context without one. Those callers must pass the country themselves —
            // narrowing to nothing here would empty required dropdowns instead.
            log.debug("No selected company country in context; model {} is not narrowed", modelName);
            return filters;
        }
        if (Filters.containsField(filters, ModelConstant.ID)) {
            // The caller named the rows — a display expansion, a by-id read, a cascade resolving
            // stored values. Narrowing those by country would hide data that legitimately belongs to
            // another one. Choosing among candidates never filters by id, so nothing that should be
            // narrowed is missed here.
            log.debug("Caller targets {} rows by id; not narrowing by country", modelName);
            return filters;
        }
        if (Filters.containsField(filters, countryField)) {
            log.debug("Caller already constrains {}.{}; keeping its condition", modelName, countryField);
            return filters;
        }
        return Filters.and(filters, Filters.of(countryField, Operator.EQUAL, EnvConstant.SELECTED_COMP_COUNTRY));
    }
}
