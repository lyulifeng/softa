package io.softa.starter.permission.spi.support;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import org.junit.jupiter.api.Test;

import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;

/**
 * The cache-aside skeleton: read → single-flight miss → resolve → back-fill,
 * fail-closed throughout. Also pins the framework default
 * {@link RedisPermissionSnapshotProvider} = "read cache, miss → deny".
 */
class AbstractCacheAsideSnapshotProviderTest {

    private static final Long TENANT = 10L;
    private static final Long USER = 42L;
    private static final String KEY = PermissionSnapshotProvider.userSnapshotKey(TENANT, USER);

    /** Mock CacheService backed by a real map so back-fill is observable. */
    private static CacheService statefulCache(Map<String, Object> store) {
        CacheService cache = mock(CacheService.class);
        when(cache.get(anyString(), eq(PermissionInfo.class)))
                .thenAnswer(inv -> store.get(inv.<String>getArgument(0)));
        doAnswer(inv -> {
            store.put(inv.getArgument(0), inv.getArgument(1));
            return null;
        }).when(cache).save(anyString(), any(), anyInt());
        return cache;
    }

    /** Template subclass whose miss-resolution is scripted per test. */
    static class TestProvider extends AbstractCacheAsideSnapshotProvider {
        final AtomicInteger resolveCount = new AtomicInteger();
        private final Supplier<PermissionInfo> resolver;

        TestProvider(CacheService cache, Supplier<PermissionInfo> resolver) {
            super(cache);
            this.resolver = resolver;
        }

        @Override
        protected PermissionInfo resolveOnMiss(Long tenantId, Long userId) {
            resolveCount.incrementAndGet();
            return resolver.get();
        }
    }

    @Test
    void cacheHit_returnsCached_withoutResolving() {
        Map<String, Object> store = new ConcurrentHashMap<>();
        PermissionInfo cached = PermissionInfo.builder().build();
        store.put(KEY, cached);
        TestProvider p = new TestProvider(statefulCache(store),
                () -> { throw new AssertionError("resolveOnMiss must not run on a hit"); });

        assertThat(p.get(TENANT, USER)).isSameAs(cached);
        assertThat(p.resolveCount).hasValue(0);
    }

    @Test
    void miss_resolvesThenBackfills_soNextReadHits() {
        Map<String, Object> store = new ConcurrentHashMap<>();
        CacheService cache = statefulCache(store);
        PermissionInfo fresh = PermissionInfo.builder().build();
        TestProvider p = new TestProvider(cache, () -> fresh);

        assertThat(p.get(TENANT, USER)).isSameAs(fresh);
        assertThat(p.resolveCount).hasValue(1);
        verify(cache).save(eq(KEY), eq(fresh), anyInt());   // backfilled

        assertThat(p.get(TENANT, USER)).isSameAs(fresh);    // served from cache now
        assertThat(p.resolveCount).hasValue(1);             // no second resolve
    }

    @Test
    void miss_resolveReturnsNull_yieldsNull_noBackfill() {
        CacheService cache = statefulCache(new ConcurrentHashMap<>());
        TestProvider p = new TestProvider(cache, () -> null);

        assertThat(p.get(TENANT, USER)).isNull();
        verify(cache, never()).save(anyString(), any(), anyInt());
    }

    @Test
    void miss_resolveThrows_failsClosedToNull() {
        CacheService cache = statefulCache(new ConcurrentHashMap<>());
        TestProvider p = new TestProvider(cache, () -> { throw new RuntimeException("boom"); });

        assertThat(p.get(TENANT, USER)).isNull();   // no propagation
    }

    @Test
    void cacheReadThrows_treatedAsMiss_resolves() {
        CacheService cache = mock(CacheService.class);
        when(cache.get(anyString(), eq(PermissionInfo.class)))
                .thenThrow(new RuntimeException("redis down"));
        PermissionInfo fresh = PermissionInfo.builder().build();
        TestProvider p = new TestProvider(cache, () -> fresh);

        assertThat(p.get(TENANT, USER)).isSameAs(fresh);
        assertThat(p.resolveCount).hasValue(1);
    }

    @Test
    void concurrentMisses_coalesceIntoSingleResolve() throws Exception {
        CacheService cache = statefulCache(new ConcurrentHashMap<>());
        PermissionInfo fresh = PermissionInfo.builder().build();
        TestProvider p = new TestProvider(cache, () -> fresh);

        int threads = 16;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            List<Callable<PermissionInfo>> tasks = new ArrayList<>();
            for (int i = 0; i < threads; i++) tasks.add(() -> p.get(TENANT, USER));
            List<Future<PermissionInfo>> results = pool.invokeAll(tasks);
            for (Future<PermissionInfo> f : results) assertThat(f.get()).isSameAs(fresh);
        } finally {
            pool.shutdownNow();
        }
        assertThat(p.resolveCount).hasValue(1);   // single-flight coalesced the herd
    }

    @Test
    void redisDefault_missMeansDeny_noResourcing() {
        CacheService cache = statefulCache(new ConcurrentHashMap<>());
        RedisPermissionSnapshotProvider p = new RedisPermissionSnapshotProvider(cache);

        assertThat(p.get(TENANT, USER)).isNull();                   // miss → fail-closed
        verify(cache, never()).save(anyString(), any(), anyInt());  // never re-sources
    }

    @Test
    void redisDefault_hit_returnsCached() {
        Map<String, Object> store = new ConcurrentHashMap<>();
        PermissionInfo cached = PermissionInfo.builder().build();
        store.put(KEY, cached);

        assertThat(new RedisPermissionSnapshotProvider(statefulCache(store)).get(TENANT, USER))
                .isSameAs(cached);
    }
}
