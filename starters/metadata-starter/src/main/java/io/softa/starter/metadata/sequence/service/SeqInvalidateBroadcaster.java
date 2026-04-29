package io.softa.starter.metadata.sequence.service;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.starter.metadata.sequence.dto.SeqInvalidateEvent;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

/**
 * Publishes {@link SeqInvalidateEvent}s to peers via Redis Pub/Sub so that
 * other instances can drop their {@link SequenceConfigCache} entries when
 * an admin updates a sequence row on the originating instance.
 *
 * <p>v1 only carries cache-evict semantics. Adding new sequence codes is a
 * release-time concern (new JSON file + run {@code loadPreTenantData}
 * before the new app instances start), so this channel never needs to
 * trigger a registry rebuild.
 *
 * <p>Failures are logged but never propagated: a missed broadcast simply
 * means peers see stale config until their 5-min TTL expires.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeqInvalidateBroadcaster {

    private final StringRedisTemplate redisTemplate;

    public void publish(String code) {
        Long tenantId = ContextHolder.getContext().getTenantId();
        SeqInvalidateEvent event = new SeqInvalidateEvent(tenantId, code);
        try {
            String payload = JsonUtils.objectToString(event);
            redisTemplate.convertAndSend(RedisConstant.SEQUENCE_INVALIDATE_CHANNEL, payload);
        } catch (Exception e) {
            log.warn("Failed to broadcast sequence invalidation tenant={} code={}: {}",
                    tenantId, code, e.getMessage());
        }
    }
}
