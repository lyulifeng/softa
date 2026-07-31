package io.softa.framework.web.filter.context;

import java.util.Map;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;

/**
 * Fills {@link Context#getSelectedCompanyCountry()} — the country that
 * {@code MultiCountryScope} narrows {@code @Model(multiCountry)} models by. The company id arrives
 * in the {@code X-Company-Id} header and is put on the context by {@link ContextBuilder}; this turns
 * it into a country.
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
 * otherwise repeat this lookup many times. Once here — and usually zero times, because the
 * company → country mapping is stable enough to cache.
 *
 * <h3>The country never comes from the client</h3>
 * The header carries only the id. Accepting a client-supplied country would let a caller choose
 * which country's value domain it sees, which is the decision the narrowing exists to make.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class SelectedCompanyCountryEnricher implements ContextEnricher {

    /** Shared with the company-scoped narrowing, so the two cannot disagree on what "the company" is. */
    private static final String COMPANY_MODEL = ModelConstant.COMPANY_MODEL;
    private static final String COUNTRY_FIELD = "country";

    private final ModelService<Long> modelService;
    private final CacheService cacheService;


    @Override
    public void enrich(Context context) {
        Long companyId = context.getSelectedCompanyId();
        if (companyId == null || !modelPresent()) {
            return;
        }
        String country = loadCached(companyId);
        if (StringUtils.isBlank(country)) {
            // Leaves the context without a country, so the narrowing skips instead of narrowing to
            // nothing — an unfiltered dropdown beats an empty required one. WARN because on a running
            // system it means either a stale header or a company row with no country.
            log.warn("Could not resolve a country for the selected company {}; "
                    + "multi-country models will not be narrowed for this request", companyId);
            return;
        }
        context.setSelectedCompanyCountry(country);
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

    String loadCached(Long companyId) {
        String key = RedisConstant.COMPANY_COUNTRY + companyId;
        String cached = cacheService.get(key, String.class);
        if (cached != null) {
            return cached;
        }
        String country = readCountryFromDb(companyId);
        if (StringUtils.isBlank(country)) {
            return null;
        }
        // Short TTL, deliberately not the long one EmpInfo uses. The country IS editable — it is an
        // ordinary field on the company's own form — so an entry made in the wrong country gets
        // corrected, and until this expires the correction has no effect: the forms keep offering the
        // old country's value domains, with nothing on screen saying why.
        //
        // A cached mapping needs either an eviction hook or a short life. There is no natural hook
        // here: companies are written through the generic model CRUD, so eviction would mean matching
        // on a model name inside a shared write path — a special case bolted onto infrastructure that
        // is otherwise model-agnostic, and one nobody would think to look for. Bounding the staleness
        // instead costs one query per company per five minutes, which against a per-request read is
        // still better than 99% saved.
        cacheService.save(key, country, RedisConstant.FIVE_MINUTES);
        return country;
    }

    /**
     * {@code @SkipPermissionCheck} keeps this read out of the scope aspect chain — the chain it would
     * re-enter is the one being set up by this very enrich pass.
     */
    @SkipPermissionCheck
    String readCountryFromDb(Long companyId) {
        Map<String, Object> row = modelService.getById(COMPANY_MODEL, companyId).orElse(null);
        if (row == null) {
            log.warn("Selected company {} does not exist in model {}", companyId, COMPANY_MODEL);
            return null;
        }
        Object country = row.get(COUNTRY_FIELD);
        return country == null ? null : country.toString().trim();
    }
}
