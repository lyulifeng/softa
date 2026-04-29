package io.softa.starter.metadata.sequence.service;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.starter.metadata.sequence.dto.SeqInvalidateEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.listener.PatternTopic;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;
import org.springframework.stereotype.Component;

/**
 * Subscribes to the {@code seq-invalidate} Redis Pub/Sub channel and
 * applies peer cache evictions locally. v1 only handles the evict path —
 * registry rebuild is a startup-only concern in this version.
 *
 * <p>Exceptions are logged but never propagated; a missed broadcast simply
 * means the local cache stays stale until its 5-min TTL expires.
 */
@Component
@Slf4j
@RequiredArgsConstructor
public class SeqInvalidateListener implements MessageListener {

    private final RedisConnectionFactory connectionFactory;
    private final SequenceConfigCache configCache;

    @PostConstruct
    void register() {
        RedisMessageListenerContainer container = new RedisMessageListenerContainer();
        container.setConnectionFactory(connectionFactory);
        container.addMessageListener(this, new PatternTopic(RedisConstant.SEQUENCE_INVALIDATE_CHANNEL));
        container.afterPropertiesSet();
        container.start();
    }

    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            SeqInvalidateEvent event = JsonUtils.stringToObject(
                    new String(message.getBody()), SeqInvalidateEvent.class);
            if (event == null || event.getCode() == null || event.getTenantId() == null) {
                return;
            }
            configCache.evictExplicit(event.getTenantId(), event.getCode());
        } catch (Exception e) {
            log.warn("Failed to handle sequence invalidation message: {}", e.getMessage());
        }
    }
}
