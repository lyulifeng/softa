package io.softa.starter.referencedata.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.CacheService;
import io.softa.starter.referencedata.entity.Currency;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CurrencyCache}'s read-through behavior. The cache runs
 * for real; only the underlying {@link CacheService} is mocked, so these assert
 * the actual miss→load→save / hit→skip-load logic — unlike a service-level test
 * where the whole cache is a mock and the "second hit skips DB" is merely scripted.
 */
class CurrencyCacheTest {

    private static final String KEY = "ref:currency:code:USD";
    private static final int TTL_SECONDS = 3600;

    private CacheService cacheService;
    private CurrencyCache cache;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        cache = new CurrencyCache();
        ReflectionTestUtils.setField(cache, "cacheService", cacheService);
    }

    @Test
    void readThroughLoadsOnMissThenServesFromCacheOnHit() {
        Currency usd = currency("USD");
        AtomicInteger loads = new AtomicInteger();
        Supplier<Currency> loader = () -> {
            loads.incrementAndGet();
            return usd;
        };
        when(cacheService.get(KEY, Currency.class))
                .thenReturn(null)   // 1st call: miss
                .thenReturn(usd);   // 2nd call: hit

        Currency first = cache.getByCode("USD", loader);
        Currency second = cache.getByCode("USD", loader);

        Assertions.assertSame(usd, first);
        Assertions.assertSame(usd, second);
        Assertions.assertEquals(1, loads.get(), "loader must run only on the cache miss");
        verify(cacheService).save(KEY, usd, TTL_SECONDS);
    }

    @Test
    void missWithNullLoadIsNotCached() {
        when(cacheService.get(KEY, Currency.class)).thenReturn(null);

        Assertions.assertNull(cache.getByCode("USD", () -> null));

        verify(cacheService, never()).save(any(), any(), anyInt());
    }

    @Test
    void nullCodeBypassesCacheEntirely() {
        Currency usd = currency("USD");

        Assertions.assertSame(usd, cache.getByCode(null, () -> usd));

        verifyNoInteractions(cacheService);
    }

    @Test
    void evictByCodeClearsKey() {
        cache.evictByCode("USD");
        verify(cacheService).clear(KEY);
    }

    @Test
    void evictByNullCodeIsNoop() {
        cache.evictByCode(null);
        verifyNoInteractions(cacheService);
    }

    private static Currency currency(String code) {
        Currency c = new Currency();
        c.setId(code);   // code-as-id
        return c;
    }
}
