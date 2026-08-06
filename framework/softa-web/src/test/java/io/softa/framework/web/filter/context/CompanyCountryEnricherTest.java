package io.softa.framework.web.filter.context;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.EmpInfo;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The country resolved here is the only thing that makes per-country narrowing happen, and every
 * failure path is silent by design: leave the context without a country and multi-country models are
 * simply not narrowed, which looks exactly like a deployment that never configured the feature.
 * Hence a test per path rather than one happy case.
 */
class CompanyCountryEnricherTest {

    private static final String COMPANY_MODEL = "LegalEntity";

    private MockedStatic<ModelManager> modelManager;

    @BeforeEach
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);
        modelManager.when(() -> ModelManager.existModel(COMPANY_MODEL)).thenReturn(true);
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    @SuppressWarnings("unchecked")
    private static ModelService<Long> models() {
        return mock(ModelService.class);
    }

    private static Context contextWith(Long companyId) {
        Context context = new Context();
        context.setCompanyId(companyId);
        return context;
    }

    /** The caller's own employing company — the affiliation, not the selection. */
    private static EmpInfo empInfoWithCompany(Long companyId) {
        EmpInfo empInfo = new EmpInfo();
        empInfo.setCompanyId(companyId);
        return empInfo;
    }

    // ---- the model has to be there ---------------------------------------

    @Test
    void anApplicationWithoutTheCompanyModelPaysNothing() {
        // An app with no company dimension is the normal case, not a misconfiguration: it must cost
        // neither a query nor an exception. Mirrors EmployeeContextEnricher degrading to no EmpInfo.
        modelManager.when(() -> ModelManager.existModel(COMPANY_MODEL)).thenReturn(false);
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);

        new CompanyCountryEnricher(models, cache).enrich(contextWith(8712L));

        verifyNoInteractions(models);
        verifyNoInteractions(cache);
    }

    @Test
    void theModelNameIsTheConventionalOne() {
        // Pinned so a rename of the HR model does not silently disable the narrowing: the framework
        // hard-codes this name, so the two have to stay in step.
        ModelService<Long> models = models();
        when(models.getById(eq("LegalEntity"), eq(8712L))).thenReturn(Optional.of(Map.of("country", "SG")));
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
    }

    // ---- resolution ------------------------------------------------------

    @Test
    void readsCountryFromTheCompanyRowAndCachesIt() {
        ModelService<Long> models = models();
        when(models.getById(eq(COMPANY_MODEL), eq(8712L))).thenReturn(Optional.of(Map.of("country", "SG")));
        CacheService cache = mock(CacheService.class);
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, cache).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
        verify(cache).save(eq(RedisConstant.COMPANY_COUNTRY + 8712L), eq("SG"),
                eq(RedisConstant.FIVE_MINUTES));
    }

    @Test
    void aCacheHitSkipsTheQuery() {
        // The whole reason this runs once per request rather than per model: narrowing is applied per
        // model, so a single page would otherwise issue the same lookup many times over.
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);
        when(cache.get(eq(RedisConstant.COMPANY_COUNTRY + 8712L), eq(String.class))).thenReturn("NZ");
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, cache).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("NZ");
        verifyNoInteractions(models);
    }

    @Test
    void aCountryIsTrimmed() {
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", " SG ")));
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
    }

    @Test
    void theCacheKeyIsPerCompany() {
        // One key per company, not per user or per request: the company → country mapping is what is
        // stable enough to cache. Caching the selection itself would defeat switching.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", "SG")));
        CacheService cache = mock(CacheService.class);
        CompanyCountryEnricher enricher = new CompanyCountryEnricher(models, cache);

        enricher.enrich(contextWith(1L));
        enricher.enrich(contextWith(2L));

        verify(cache, times(1)).get(eq(RedisConstant.COMPANY_COUNTRY + 1L), eq(String.class));
        verify(cache, times(1)).get(eq(RedisConstant.COMPANY_COUNTRY + 2L), eq(String.class));
    }

    // ---- the silent paths ------------------------------------------------

    @Test
    void noCompanyAtAllTouchesNothing() {
        // Neither selected nor affiliated: anonymous requests (a public form), service-to-service
        // calls, a pure administrator who is not an employee, and any client that predates the header.
        // None may pay for a lookup.
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);

        new CompanyCountryEnricher(models, cache).enrich(contextWith(null));

        verifyNoInteractions(models);
        verifyNoInteractions(cache);
    }

    // ---- the fallback ----------------------------------------------------

    @Test
    void fallsBackToTheCompanyTheCallerBelongsTo() {
        // A role granted no company selects nothing, so no header goes out and there is no country to
        // narrow by — leaving a self-service employee looking at every country's value domains. They
        // belong to exactly one company, so that country is never in doubt.
        ModelService<Long> models = models();
        when(models.getById(anyString(), eq(4242L))).thenReturn(Optional.of(Map.of("country", "SG")));
        Context context = contextWith(null);
        context.setEmpInfo(empInfoWithCompany(4242L));

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
        // The selection itself stays empty — it is what tells the scope layer this is the fallback and
        // not a header, which is what keeps the SELECTED_COMP_COUNTRY placeholder resolving to null.
        assertThat(context.getCompanyId()).isNull();
    }

    @Test
    void theSelectionWinsOverTheAffiliation() {
        // The whole point of the header for a multi-company user: looking at another of their companies
        // must show that company's value domains, not the one they happen to be employed by.
        ModelService<Long> models = models();
        when(models.getById(anyString(), eq(8712L))).thenReturn(Optional.of(Map.of("country", "NZ")));
        Context context = contextWith(8712L);
        context.setEmpInfo(empInfoWithCompany(4242L));

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("NZ");
        verify(models, never()).getById(anyString(), eq(4242L));
    }

    @Test
    void anEmployeeWithNoCompanyIsNotAFallback() {
        // EmpInfo is present but carries no legal entity — an employee record mid-setup. Resolving
        // nothing beats resolving null and narrowing every value domain to country = NULL.
        ModelService<Long> models = models();
        Context context = contextWith(null);
        context.setEmpInfo(empInfoWithCompany(null));

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
        verifyNoInteractions(models);
    }

    @Test
    void aMissingCompanyLeavesTheContextAlone() {
        // A stale header — another tenant's id left in a browser, or a row since deleted. Must not fail
        // the request: the selection is a view preference, not an authorization.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.empty());
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
    }

    @Test
    void aCompanyWithoutACountryLeavesTheContextAlone() {
        ModelService<Long> models = models();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Acme SG");
        when(models.getById(anyString(), any())).thenReturn(Optional.of(row));
        Context context = contextWith(8712L);

        new CompanyCountryEnricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
    }

    @Test
    void theCachedCountryIsShortLived() {
        // The country is editable on the company's own form, and there is no eviction hook (companies
        // are written through generic CRUD). A long TTL would keep serving the pre-correction country
        // — forms offering the wrong country's value domains, silently. Pinned so nobody "optimises"
        // this back up to the long TTL that EmpInfo uses.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", "SG")));
        CacheService cache = mock(CacheService.class);

        new CompanyCountryEnricher(models, cache).enrich(contextWith(8712L));

        verify(cache).save(anyString(), any(), eq(RedisConstant.FIVE_MINUTES));
        assertThat(RedisConstant.FIVE_MINUTES).isLessThanOrEqualTo(RedisConstant.ONE_HOUR);
    }

    @Test
    void aBlankCountryIsNotCached() {
        // Caching a miss would pin "do not narrow" for a month — far past the mistake that caused it.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", "  ")));
        CacheService cache = mock(CacheService.class);

        new CompanyCountryEnricher(models, cache).enrich(contextWith(8712L));

        verify(cache, never()).save(anyString(), any(), anyInt());
    }

}
