package io.softa.starter.referencedata.support;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.CacheService;
import io.softa.starter.referencedata.entity.CountryRegion;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link CountryRegionCache}'s read-through behavior. The cache
 * runs for real; only the underlying {@link CacheService} is mocked, so these
 * assert the actual miss→load→save / hit→skip-load logic — unlike a service-level
 * test where the whole cache is a mock and the "second hit skips DB" is merely scripted.
 */
class CountryRegionCacheTest {

    private static final String KEY = "ref:country-region:code:CN";
    private static final int TTL_SECONDS = 3600;

    private CacheService cacheService;
    private CountryRegionCache cache;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        cache = new CountryRegionCache();
        ReflectionTestUtils.setField(cache, "cacheService", cacheService);
    }

    @Test
    void readThroughLoadsOnMissThenServesFromCacheOnHit() {
        CountryRegion cn = country("CN");
        AtomicInteger loads = new AtomicInteger();
        Supplier<CountryRegion> loader = () -> {
            loads.incrementAndGet();
            return cn;
        };
        when(cacheService.get(KEY, CountryRegion.class))
                .thenReturn(null)   // 1st call: miss
                .thenReturn(cn);    // 2nd call: hit

        CountryRegion first = cache.getByCode("CN", loader);
        CountryRegion second = cache.getByCode("CN", loader);

        Assertions.assertSame(cn, first);
        Assertions.assertSame(cn, second);
        Assertions.assertEquals(1, loads.get(), "loader must run only on the cache miss");
        verify(cacheService).save(KEY, cn, TTL_SECONDS);
    }

    @Test
    void missWithNullLoadIsNotCached() {
        when(cacheService.get(KEY, CountryRegion.class)).thenReturn(null);

        Assertions.assertNull(cache.getByCode("CN", () -> null));

        verify(cacheService, never()).save(any(), any(), anyInt());
    }

    @Test
    void nullCodeBypassesCacheEntirely() {
        CountryRegion cn = country("CN");

        Assertions.assertSame(cn, cache.getByCode(null, () -> cn));

        verifyNoInteractions(cacheService);
    }

    @Test
    void evictByCodeClearsKey() {
        cache.evictByCode("CN");
        verify(cacheService).clear(KEY);
    }

    @Test
    void evictByNullCodeIsNoop() {
        cache.evictByCode(null);
        verifyNoInteractions(cacheService);
    }

    private static CountryRegion country(String code) {
        CountryRegion c = new CountryRegion();
        c.setId(code);   // code-as-id
        return c;
    }
}
