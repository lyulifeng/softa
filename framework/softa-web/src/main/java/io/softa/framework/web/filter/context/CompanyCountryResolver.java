package io.softa.framework.web.filter.context;

import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Component;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Company id → ISO 3166-1 alpha-2 country, read from the company model and cached.
 *
 * <p>One lookup, two callers: {@link CompanyCountryEnricher} resolves the country of the company the
 * caller belongs to, and the import pipeline resolves the country of the company a <i>row</i> names,
 * so that a value in a country-partitioned domain ("Passport", "Full Time") is looked up in that
 * row's country rather than the importer's. Both used to live inside the enricher; the import needed
 * the same read and the same cache, and a second copy is how the two drift.
 *
 * <h3>Convention, not configuration</h3>
 * Reads {@code Company.country} by name — the framework holds a company <b>slot</b> but no company
 * <b>concept</b>. An application whose model is absent gets {@code null} for every id, at no cost.
 *
 * <h3>Cache</h3>
 * Short TTL on purpose. The country IS editable — an ordinary field on the company's own form — so an
 * entry made in the wrong country gets corrected, and until this expires the correction has no
 * effect. There is no natural eviction hook (companies are written through the generic model CRUD),
 * so the staleness is bounded by time instead: one query per company per five minutes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class CompanyCountryResolver {

    private static final String COMPANY_MODEL = ModelConstant.COMPANY_MODEL;
    private static final String COUNTRY_FIELD = ModelConstant.COUNTRY_FIELD;

    private final ModelService<Long> modelService;
    private final CacheService cacheService;

    /**
     * The country of a company, or {@code null} when the id is null, the company model is absent, the
     * row does not exist or carries no country. Never throws: every caller treats "unknown" as "do not
     * narrow", and a lookup failure must not fail the request it is enriching.
     */
    public String resolveCountry(Long companyId) {
        if (companyId == null || !ModelManager.existModel(COMPANY_MODEL)) {
            return null;
        }
        String key = RedisConstant.COMPANY_COUNTRY + companyId;
        String cached = cacheService.get(key, String.class);
        if (cached != null) {
            return cached;
        }
        String country = readCountryFromDb(companyId);
        if (StringUtils.isBlank(country)) {
            return null;
        }
        cacheService.save(key, country, RedisConstant.FIVE_MINUTES);
        return country;
    }

    /**
     * Read on a copy of the request context with permission checks waived — the read must not
     * re-enter the scope chain that may be asking for it (the enricher runs while the context is
     * being built; the import runs under the importer's own row scopes, which need not cover the
     * company a row names). Applied in code rather than by annotation: an annotation is proxy advice
     * and does not fire on a self-invocation. Tenant isolation still applies.
     */
    String readCountryFromDb(Long companyId) {
        if (!ContextHolder.existContext()) {
            return readCountry(companyId);
        }
        Context isolated = ContextHolder.getContext().copy();
        isolated.setSkipPermissionCheck(true);
        return ContextHolder.callWith(isolated, () -> readCountry(companyId));
    }

    private String readCountry(Long companyId) {
        Map<String, Object> row = modelService.getById(COMPANY_MODEL, companyId).orElse(null);
        if (row == null) {
            log.warn("Company {} does not exist in model {}", companyId, COMPANY_MODEL);
            return null;
        }
        Object country = row.get(COUNTRY_FIELD);
        return country == null ? null : country.toString().trim();
    }
}
