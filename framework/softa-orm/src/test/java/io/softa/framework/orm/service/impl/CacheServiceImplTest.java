package io.softa.framework.orm.service.impl;

import java.time.Duration;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentMatchers;
import org.mockito.Mockito;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.constant.RedisConstant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Pins the root-key prefixing contract: the prefix is applied exactly once,
 * in the overload that touches Redis, so writes and reads resolve the same
 * physical key.
 *
 * <p>Regression: the default-TTL {@code save(key, object)} used to resolve
 * the key path before delegating to {@code save(key, object, seconds)},
 * which resolved it again — writes landed on {@code root:root:key} while
 * reads looked up {@code root:key}, so those cache entries never hit.
 */
class CacheServiceImplTest {

    private static final String ROOT_KEY = "softa";

    private StringRedisTemplate stringRedisTemplate;
    private ValueOperations<String, String> valueOperations;
    private CacheServiceImpl cacheService;

    @BeforeEach
    void setUp() {
        stringRedisTemplate = Mockito.mock(StringRedisTemplate.class);
        valueOperations = Mockito.mock();
        when(stringRedisTemplate.opsForValue()).thenReturn(valueOperations);
        cacheService = new CacheServiceImpl();
        ReflectionTestUtils.setField(cacheService, "rootKey", ROOT_KEY);
        ReflectionTestUtils.setField(cacheService, "stringRedisTemplate", stringRedisTemplate);
    }

    @Test
    void saveWithDefaultTtlPrefixesRootKeyOnce() {
        cacheService.save("tenant:info:1", "cached");

        verify(valueOperations).set(eq(ROOT_KEY + ":tenant:info:1"), anyString(),
                eq(Duration.ofSeconds(RedisConstant.DEFAULT_EXPIRE_SECONDS)));
    }

    @Test
    void saveAndGetResolveTheSamePhysicalKey() {
        cacheService.save("tenant:info:1", "cached");
        cacheService.get("tenant:info:1");

        verify(valueOperations).set(eq(ROOT_KEY + ":tenant:info:1"), anyString(),
                eq(Duration.ofSeconds(RedisConstant.DEFAULT_EXPIRE_SECONDS)));
        verify(valueOperations).get(ROOT_KEY + ":tenant:info:1");
    }

    @Test
    void saveWithZeroSecondsWritesWithoutTtl() {
        cacheService.save("tenant:info:1", "cached", 0);

        verify(valueOperations).set(eq(ROOT_KEY + ":tenant:info:1"), anyString());
    }

    @Test
    void incrementRunsTheAtomicScriptOnThePrefixedKey() {
        when(stringRedisTemplate.execute(ArgumentMatchers.<RedisScript<Long>>any(),
                eq(List.of(ROOT_KEY + ":login:attempts:42")), eq("30"))).thenReturn(2L);

        assertEquals(2L, cacheService.increment("login:attempts:42", 30));
    }

    @Test
    void blankRootKeyLeavesKeyUntouched() {
        ReflectionTestUtils.setField(cacheService, "rootKey", "");

        assertEquals("tenant:info:1", cacheService.getKeyPath("tenant:info:1"));
    }
}
