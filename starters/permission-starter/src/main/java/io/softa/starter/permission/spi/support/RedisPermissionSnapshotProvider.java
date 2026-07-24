package io.softa.starter.permission.spi.support;

import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;

/**
 * Framework default {@link PermissionSnapshotProvider} — a fail-closed shared-cache
 * reader: it reads {@code perm:{tenant}:user:{user}} and, on a miss,
 * {@linkplain #resolveOnMiss re-sources nothing} → returns {@code null} → callers
 * fail-closed (deny). Registered by {@code PermissionStarterAutoConfiguration} only
 * when no other provider is present ({@code @ConditionalOnMissingBean}); in the
 * monolith {@code user-starter}'s {@code PermissionInfoEnricher} wins.
 *
 * <p>Correct as the safe default for a keep-warm topology: some principal (running
 * the enricher) has already populated the shared cache, so this reader just serves
 * it. When a deployment instead needs <b>fetch-on-miss</b> (a pure-enforce
 * microservice with a cold/expired entry), it provides its own
 * {@link AbstractCacheAsideSnapshotProvider} subclass that implements
 * {@link #resolveOnMiss} (session-forward to {@code /me/uiContext}, RPC, DB
 * rebuild, …) — that bean wins via {@code @ConditionalOnMissingBean}. The
 * transport is intentionally out of the framework.
 */
public class RedisPermissionSnapshotProvider extends AbstractCacheAsideSnapshotProvider {

    public RedisPermissionSnapshotProvider(CacheService cacheService) {
        super(cacheService);
    }

    /** No in-process re-sourcing — a cache miss means deny. */
    @Override
    protected PermissionInfo resolveOnMiss(Long tenantId, Long userId) {
        return null;
    }
}
