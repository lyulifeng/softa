package io.softa.starter.metadata.sequence.service;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.sequence.exception.SequenceNotFoundException;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.metadata.sequence.entity.SysSequence;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Thin cache for {@link SysSequence} configuration rows, keyed by
 * {@code (tenantId, code)}. Delegates Redis I/O to softa-orm's
 * {@link CacheService} (which handles {@code rootKey} prefix and
 * Jackson serialization). The full Redis key looks like
 * {@code {rootKey}:seq-config:{tenantId}:{code}}.
 *
 * <p>Cache contents reflect only the <strong>immutable</strong> portion of
 * a sequence row (template / startValue / cadence / mode / status /
 * description). The mutable counter fields ({@code currentValue} /
 * {@code lastResetKey}) are still serialized into the cached object but are
 * <strong>never read from the cache</strong> by the allocation path —
 * those go through the row-locked {@code UPDATE sys_sequence ...
 * LAST_INSERT_ID(...)} every time.
 *
 * <p>{@link SysSequenceServiceImpl} (mutating wrapper) calls
 * {@link #evict(String)} after every successful update, then broadcasts the
 * invalidation to peer instances via Redis Pub/Sub
 * ({@code seq-invalidate} channel).
 */
@Component
@RequiredArgsConstructor
public class SequenceConfigCache {

    private final CacheService cacheService;
    private final SysSequenceService sysSequenceService;

    /**
     * Resolve a config snapshot. Cache hit returns immediately; on miss, runs a
     * tenant-scoped {@code searchOne} and writes the result back.
     *
     * @throws SequenceNotFoundException if no row exists for (currentTenant, code)
     */
    public SysSequence load(String code) {
        Long tenantId = ContextHolder.getContext().getTenantId();
        String key = key(tenantId, code);

        SysSequence cached = cacheService.get(key, SysSequence.class);
        if (cached != null) {
            return cached;
        }

        FlexQuery q = new FlexQuery(new Filters().eq(SysSequence::getCode, code));
        SysSequence row = sysSequenceService.searchOne(q)
                .orElseThrow(() -> new SequenceNotFoundException(code));
        cacheService.save(key, row, RedisConstant.FIVE_MINUTES);
        return row;
    }

    /**
     * Drop the cached snapshot for (currentTenant, code). Idempotent.
     * Caller is responsible for broadcasting if running in a cluster.
     */
    public void evict(String code) {
        Long tenantId = ContextHolder.getContext().getTenantId();
        cacheService.clear(key(tenantId, code));
    }

    /**
     * Drop the cached snapshot for an explicit (tenantId, code), bypassing
     * the {@link ContextHolder}. Used by the cluster invalidation listener,
     * which receives broadcasts without holding the originating tenant
     * context.
     */
    public void evictExplicit(Long tenantId, String code) {
        cacheService.clear(key(tenantId, code));
    }

    private static String key(Long tenantId, String code) {
        return RedisConstant.SEQUENCE_CONFIG + tenantId + ":" + code;
    }
}
