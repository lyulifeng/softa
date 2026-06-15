package io.softa.starter.tenant.service.impl;

import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.enums.TenantStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;

/**
 * Tenant lifecycle gate primitives: {@link TenantInfoServiceImpl#isTenantActive} and the
 * {@link TenantInfoServiceImpl#deactivate} choke point (Model 1 — login + per-request gate).
 */
class TenantInfoServiceImplTest {

    private static TenantInfo tenant(TenantStatus status) {
        TenantInfo t = new TenantInfo();
        t.setId(1L);
        t.setStatus(status);
        return t;
    }

    @Test
    void isTenantActive_trueOnlyForActiveTenant() {
        TenantInfoServiceImpl svc = Mockito.spy(new TenantInfoServiceImpl());
        doReturn(tenant(TenantStatus.ACTIVE)).when(svc).getTenantInfo(1L);
        doReturn(tenant(TenantStatus.SUSPENDED)).when(svc).getTenantInfo(2L);
        doReturn(null).when(svc).getTenantInfo(3L);

        assertTrue(svc.isTenantActive(1L), "ACTIVE tenant is active");
        assertFalse(svc.isTenantActive(2L), "SUSPENDED tenant is not active");
        assertFalse(svc.isTenantActive(3L), "missing tenant is not active");
        assertFalse(svc.isTenantActive(null), "null tenantId is not active");
    }

    @Test
    void deactivate_suspendsTenantAndEvictsCaches() {
        TenantInfoServiceImpl svc = Mockito.spy(new TenantInfoServiceImpl());
        CacheService cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(svc, "cacheService", cacheService);

        TenantInfo tenant = tenant(TenantStatus.ACTIVE);
        doReturn(Optional.of(tenant)).when(svc).getById(1L);
        doReturn(true).when(svc).updateOne(any(TenantInfo.class));

        svc.deactivate(1L);

        assertEquals(TenantStatus.SUSPENDED, tenant.getStatus(), "deactivate sets status to SUSPENDED");
        assertNotNull(tenant.getSuspendedTime(), "deactivate stamps suspendedTime");
        verify(svc).updateOne(tenant);
        // Both tenant caches evicted so isTenantActive() / active-id filtering flip immediately.
        verify(cacheService).clear(RedisConstant.TENANT_INFO + 1L);
        verify(cacheService).clear(RedisConstant.TENANT_IDS);
    }
}
