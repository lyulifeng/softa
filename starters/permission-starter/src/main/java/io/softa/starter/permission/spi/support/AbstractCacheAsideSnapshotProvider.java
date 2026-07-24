package io.softa.starter.permission.spi.support;

import java.util.concurrent.ConcurrentHashMap;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import lombok.extern.slf4j.Slf4j;

/**
 * Reusable cache-aside skeleton for {@link PermissionSnapshotProvider} — the
 * framework owns the (error-prone) generic algorithm; a deployment supplies only
 * the transport via {@link #resolveOnMiss}.
 *
 * <h3>Where the framework line is</h3>
 * {@code permission-starter} defines the <b>contract</b> (this SPI) and the
 * <b>in-process mechanics</b> (this template): shared-cache read → single-flight
 * on miss → back-fill → fail-closed. It deliberately owns <b>no deployment
 * topology</b> — transport (RPC / DB rebuild), service discovery (URL / naming),
 * and service-to-service auth all live in the subclass a deployment provides.
 * The framework ships exactly one concrete subclass,
 * {@link RedisPermissionSnapshotProvider} (a fail-closed cache reader, the safe
 * default); a microservice re-sourcing from a principal simply extends this and
 * implements {@link #resolveOnMiss}.
 *
 * <h3>Algorithm ({@code get} is final)</h3>
 * <ol>
 *   <li>Read the shared cache at {@link PermissionSnapshotProvider#userSnapshotKey}
 *       — hit → return.</li>
 *   <li>Miss → enter a per-key single-flight so concurrent requests for the same
 *       cold user fire one {@link #resolveOnMiss}, not a thundering herd.</li>
 *   <li>Re-check the cache (a peer may have filled it while we waited), else call
 *       {@link #resolveOnMiss}; back-fill the cache when it returns non-null.</li>
 *   <li>Any failure — cache error, {@code resolveOnMiss} throwing, or a
 *       {@code null} result — yields {@code null} → callers fail-closed (deny).</li>
 * </ol>
 */
@Slf4j
public abstract class AbstractCacheAsideSnapshotProvider implements PermissionSnapshotProvider {

    private final CacheService cacheService;

    /** Per-key locks so a cold user's concurrent requests coalesce into one
     *  {@link #resolveOnMiss}. Entries are created on miss and removed in the
     *  {@code finally}, so the map only ever holds in-flight keys. */
    private final ConcurrentHashMap<String, Object> inFlight = new ConcurrentHashMap<>();

    protected AbstractCacheAsideSnapshotProvider(CacheService cacheService) {
        this.cacheService = cacheService;
    }

    @Override
    public final PermissionInfo get(Long tenantId, Long userId) {
        String key = PermissionSnapshotProvider.userSnapshotKey(tenantId, userId);
        PermissionInfo hit = safeRead(key);
        if (hit != null) return hit;
        return loadCoalesced(key, tenantId, userId);
    }

    private PermissionInfo loadCoalesced(String key, Long tenantId, Long userId) {
        Object lock = inFlight.computeIfAbsent(key, k -> new Object());
        synchronized (lock) {
            try {
                // A peer holding the same lock may have back-filled while we waited.
                PermissionInfo again = safeRead(key);
                if (again != null) return again;

                PermissionInfo resolved;
                try {
                    resolved = resolveOnMiss(tenantId, userId);
                } catch (Throwable t) {
                    log.warn("SnapshotProvider — resolveOnMiss failed for {}; fail-closed", key, t);
                    return null;
                }
                if (resolved != null) backfill(key, resolved);
                return resolved;
            } finally {
                inFlight.remove(key, lock);
            }
        }
    }

    /**
     * Produce the snapshot when it is absent from the shared cache.
     *
     * <p>The framework default returns {@code null} (fail-closed) — see
     * {@link RedisPermissionSnapshotProvider}. A cross-process deployment
     * overrides this to re-source from its principal (session-forward to
     * {@code /me/uiContext}, an internal RPC, a local DB rebuild, …). The impl
     * MUST NOT cache the result itself — the template back-fills — and should
     * return {@code null} on any failure so the caller fails closed.
     */
    protected abstract PermissionInfo resolveOnMiss(Long tenantId, Long userId);

    /** TTL (seconds) for back-filled entries. Override to tune; defaults to 1h,
     *  matching the producer-side ({@code PermissionInfoEnricher}) TTL. */
    protected int cacheTtlSeconds() {
        return RedisConstant.ONE_HOUR;
    }

    private PermissionInfo safeRead(String key) {
        try {
            return cacheService.get(key, PermissionInfo.class);
        } catch (Throwable t) {
            log.warn("SnapshotProvider — cache read {} failed; treating as miss", key, t);
            return null;
        }
    }

    private void backfill(String key, PermissionInfo pi) {
        try {
            cacheService.save(key, pi, cacheTtlSeconds());
        } catch (Throwable t) {
            log.warn("SnapshotProvider — cache back-fill {} failed; continuing", key, t);
        }
    }
}
