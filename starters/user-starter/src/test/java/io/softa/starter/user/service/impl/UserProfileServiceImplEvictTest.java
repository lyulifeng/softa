package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.orm.service.CacheService;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

/**
 * {@link UserProfileServiceImpl#evictUserInfo} — the invalidation half of the {@code UserInfo}
 * cache. Pins the key so it can never drift from the one {@code getUserInfo} reads and
 * {@code ContextBuilder} depends on: a mismatch here is silent (nothing throws, the eviction simply
 * misses) and reopens the split-brain this method exists to close — the login gate reading
 * {@code UserAccount.status} from the database while every subsequent request reads a month-old
 * {@code UserInfo.active} from Redis.
 */
class UserProfileServiceImplEvictTest {

    private CacheService cacheService;
    private UserProfileServiceImpl service;

    @BeforeEach
    void setUp() {
        cacheService = mock(CacheService.class);
        service = new UserProfileServiceImpl();
        ReflectionTestUtils.setField(service, "cacheService", cacheService);
    }

    @Test
    void evictUserInfo_clearsTheKeyGetUserInfoReads() {
        service.evictUserInfo(42L);
        verify(cacheService).clear(RedisConstant.USER_INFO + 42L);
    }

    @Test
    void evictUserInfo_nullUserId_noop() {
        service.evictUserInfo(null);
        // Guard against clearing a "user_info:null" key — harmless in itself, but it would mask a
        // caller that lost track of the id and thinks it invalidated something.
        verify(cacheService, never()).clear(anyString());
    }
}
