package io.softa.framework.web.filter.context;


import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.EmpInfo;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Fills {@link Context#getCompanyCountry()} — the country of the company the caller <b>belongs to</b>,
 * read through {@code EmpInfo.companyId}. The affiliation, in one word.
 *
 * <p>This used to resolve the company selected in a header switcher first and fall back to the
 * affiliation only when nothing was selected. The switcher is gone, so the fallback is all that is
 * left, and it serves one purpose: the caller whose roles reach no company — a self-service employee —
 * has an empty country set on the context, and {@code MultiCountryScope} would otherwise show them
 * every country's value domains. They belong to exactly one company, so its country is never in doubt.
 * For everyone else the narrowing reads {@code Context.accessibleCountries}, which the permission layer
 * bridges from the grant, and this field is not consulted.
 *
 * <h3>Convention, not configuration</h3>
 * The lookup carries no domain knowledge — read a model by id, take a field, cache it. The two names
 * are hard-coded conventions, exactly as {@code EmployeeContextEnricher} hard-codes {@code Employee}
 * and {@code Department}: the framework holds a company <b>slot</b> but no company <b>concept</b> (no
 * {@code Company} model, no owning module), so it names the convention and steps aside when an
 * application does not follow it. An application whose model is named differently supplies its own
 * {@link ContextEnricher} — the SPI is already the extension point, so no configuration surface is
 * added until something actually needs one.
 *
 * <h3>Resolved once per request, on purpose</h3>
 * Narrowing is applied per model, so a single page (a table, a few dropdowns, a count) would
 * otherwise repeat this lookup many times. Once here, through {@link CompanyCountryResolver} — and
 * usually zero times, because the company → country mapping is stable enough to cache.
 *
 * <h3>The country never comes from the client</h3>
 * Nothing in the request names a company or a country. Accepting a client-supplied country would let a
 * caller choose which country's value domain it sees, which is the decision the narrowing exists to make.
 */
@Slf4j
@Component
@Order(ContextEnricher.ORDER_DERIVED)
@RequiredArgsConstructor
public class CompanyCountryEnricher implements ContextEnricher {

    private static final String COMPANY_MODEL = ModelConstant.COMPANY_MODEL;

    private final CompanyCountryResolver countryResolver;

    @Override
    public void enrich(Context context) {
        if (!modelPresent()) {
            return;
        }
        Long companyId = ownCompanyId(context);
        if (companyId == null) {
            return;
        }
        String country = countryResolver.resolveCountry(companyId);
        if (StringUtils.isBlank(country)) {
            // Leaves the context without a country. For a caller with a country set of their own that
            // changes nothing; for a self-service employee the narrowing then skips instead of narrowing
            // to nothing — an unfiltered dropdown beats an empty required one. WARN because on a running
            // system it means a company row with no country.
            log.warn("Could not resolve a country for company {}; "
                    + "multi-country models fall back to the caller's country set for this request", companyId);
            return;
        }
        context.setCompanyCountry(country);
    }

    /**
     * The company the caller belongs to.
     *
     * <p>Without this a role that can reach no company at all sees <b>every</b> country's value
     * domains: its country set is empty, so there is nothing to narrow by. That is a self-service
     * employee — the one user for whom the right country is never in doubt, since they belong to
     * exactly one company. The narrowing is data correctness rather than authorization (see
     * {@code MultiCountryScope}), and showing someone another country's pass types is wrong regardless
     * of what they are allowed to read.
     *
     * <p>Reads {@code EmpInfo} rather than resolving the employee itself: this enricher deliberately
     * carries no domain knowledge (see the class comment), and duplicating the {@code Employee} lookup
     * would put the same convention in a second place. Hence {@link ContextEnricher#ORDER_DERIVED} —
     * and hence a null-safe read, because the writer lives in an optional starter.
     */
    private Long ownCompanyId(Context context) {
        EmpInfo empInfo = context.getEmpInfo();
        if (empInfo == null || empInfo.getCompanyId() == null) {
            // A pure user (an administrator who is not an employee), a non-HR app, or a
            // service-to-service call: no company to fall back to, so nothing is narrowed.
            log.debug("No company selected and no employing company on the context; "
                    + "multi-country models are not narrowed for this request");
            return null;
        }
        return empInfo.getCompanyId();
    }

    /**
     * An absent model turns the resolution off rather than failing the request — an application with
     * no company dimension is the normal case, not a misconfiguration. Same shape as
     * {@code EmployeeContextEnricher} degrading to no {@code EmpInfo}.
     */
    private boolean modelPresent() {
        if (ModelManager.existModel(COMPANY_MODEL)) {
            return true;
        }
        log.debug("No '{}' model in this application; the selected company's country is not resolved "
                + "and multi-country models are not narrowed", COMPANY_MODEL);
        return false;
    }
}
