package io.softa.framework.web.filter.context;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

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
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;

/**
 * The country resolved here is what keeps a self-service employee — whose roles reach no company, so
 * whose country set is empty — inside their own country's value domains. Every failure path is silent
 * by design: leave the context without a country and the narrowing simply skips, which looks exactly
 * like a deployment that never configured the feature. Hence a test per path rather than one happy case.
 */
class CompanyCountryEnricherTest {

    private static final String COMPANY_MODEL = ModelConstant.COMPANY_MODEL;

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

    private static CompanyCountryEnricher enricher(ModelService<Long> models, CacheService cache) {
        return new CompanyCountryEnricher(new CompanyCountryResolver(models, cache));
    }

    /** A caller employed by the given company — the affiliation, the only input left. */
    private static Context employedAt(Long companyId) {
        Context context = new Context();
        EmpInfo empInfo = new EmpInfo();
        empInfo.setCompanyId(companyId);
        context.setEmpInfo(empInfo);
        return context;
    }

    // ---- the model has to be there ---------------------------------------

    @Test
    void anApplicationWithoutTheCompanyModelPaysNothing() {
        // An app with no company dimension is the normal case, not a misconfiguration: it must cost
        // neither a query nor an exception. Mirrors EmployeeContextEnricher degrading to no EmpInfo.
        modelManager.when(() -> ModelManager.existModel(COMPANY_MODEL)).thenReturn(false);
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);

        enricher(models, cache).enrich(employedAt(8712L));

        verifyNoInteractions(models);
        verifyNoInteractions(cache);
    }

    @Test
    void theModelNameIsTheConventionalOne() {
        // Pinned so a rename of the HR model does not silently disable the narrowing: the framework
        // hard-codes this name, so the two have to stay in step. The literal is deliberate and the
        // only line here that may be one.
        assertThat(COMPANY_MODEL).isEqualTo("Company");

        ModelService<Long> models = models();
        when(models.getById(eq(COMPANY_MODEL), eq(8712L))).thenReturn(Optional.of(Map.of("country", "SG")));
        Context context = employedAt(8712L);

        enricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
    }

    // ---- resolution ------------------------------------------------------

    @Test
    void readsCountryFromTheCompanyRowAndCachesIt() {
        ModelService<Long> models = models();
        when(models.getById(eq(COMPANY_MODEL), eq(8712L))).thenReturn(Optional.of(Map.of("country", "SG")));
        CacheService cache = mock(CacheService.class);
        Context context = employedAt(8712L);

        enricher(models, cache).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
        verify(cache).save(eq(RedisConstant.COMPANY_COUNTRY + 8712L), eq("SG"), eq(RedisConstant.FIVE_MINUTES));
    }

    @Test
    void aCacheHitSkipsTheQuery() {
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);
        when(cache.get(eq(RedisConstant.COMPANY_COUNTRY + 8712L), eq(String.class))).thenReturn("NZ");
        Context context = employedAt(8712L);

        enricher(models, cache).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("NZ");
        verifyNoInteractions(models);
    }

    @Test
    void aCountryIsTrimmed() {
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", " SG ")));
        Context context = employedAt(8712L);

        enricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isEqualTo("SG");
    }

    @Test
    void theCacheKeyIsPerCompany() {
        // One key per company, not per user: the company → country mapping is what is stable enough to
        // cache, and the same company's country serves every employee of it.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.of(Map.of("country", "SG")));
        CacheService cache = mock(CacheService.class);
        CompanyCountryEnricher enricher = enricher(models, cache);

        enricher.enrich(employedAt(1L));
        enricher.enrich(employedAt(2L));

        verify(cache, times(1)).get(eq(RedisConstant.COMPANY_COUNTRY + 1L), eq(String.class));
        verify(cache, times(1)).get(eq(RedisConstant.COMPANY_COUNTRY + 2L), eq(String.class));
    }

    // ---- the silent paths ------------------------------------------------

    @Test
    void aPureUserTouchesNothing() {
        // No employee record: an administrator who is not an employee, anonymous requests, a
        // service-to-service call. None may pay for a lookup, and their country set — not this — is
        // what the narrowing reads.
        ModelService<Long> models = models();
        CacheService cache = mock(CacheService.class);

        enricher(models, cache).enrich(new Context());

        verifyNoInteractions(models);
        verifyNoInteractions(cache);
    }

    @Test
    void anEmployeeWithNoCompanyIsNotAFallback() {
        // EmpInfo is present but carries no company — an employee record mid-setup. Resolving nothing
        // beats resolving null and narrowing every value domain to country = NULL.
        ModelService<Long> models = models();
        Context context = employedAt(null);

        enricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
        verifyNoInteractions(models);
    }

    @Test
    void aMissingCompanyLeavesTheContextAlone() {
        // A row since deleted, or an EmpInfo cached before a company was removed. Must not fail the
        // request: the country is a narrowing input, not an authorization.
        ModelService<Long> models = models();
        when(models.getById(anyString(), any())).thenReturn(Optional.empty());
        Context context = employedAt(8712L);

        enricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
    }

    @Test
    void aCompanyWithoutACountryLeavesTheContextAlone() {
        ModelService<Long> models = models();
        Map<String, Object> row = new HashMap<>();
        row.put("name", "Acme SG");
        when(models.getById(anyString(), any())).thenReturn(Optional.of(row));
        Context context = employedAt(8712L);

        enricher(models, mock(CacheService.class)).enrich(context);

        assertThat(context.getCompanyCountry()).isNull();
    }
}
