package io.softa.starter.tenant.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.enums.TenantStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
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

    // ─── the other two transitions on the same choke point ───

    @Test
    @DisplayName("activate restores a suspended tenant and clears the suspension stamp")
    void activate_reinstatesSuspendedTenantAndClearsStamp() {
        // Exactly one of the three timestamps is ever set, which keeps the trio a function of the status: an
        // ACTIVE tenant that still displayed a suspended time would read as suspended to anyone scanning the
        // list.
        TenantInfo tenant = tenant(TenantStatus.SUSPENDED);
        tenant.setSuspendedTime(LocalDateTime.of(2026, 7, 1, 9, 0));
        TenantInfoServiceImpl svc = serviceFor(tenant);

        svc.activate(1L);

        assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
        assertNotNull(tenant.getActivatedTime(), "activate stamps activatedTime");
        assertNull(tenant.getSuspendedTime(), "the stale suspension stamp has to be cleared");
    }

    @Test
    @DisplayName("close is reachable from both active and suspended")
    void close_reachableFromActiveAndSuspended() {
        // A customer can be shut down directly, or after a suspension that was never lifted. Allowing only
        // one of those would leave suspended tenants permanently unclosable.
        TenantInfo fromActive = tenant(TenantStatus.ACTIVE);
        serviceFor(fromActive).close(1L);
        assertEquals(TenantStatus.CLOSED, fromActive.getStatus());
        assertNotNull(fromActive.getClosedTime());

        TenantInfo fromSuspended = tenant(TenantStatus.SUSPENDED);
        serviceFor(fromSuspended).close(1L);
        assertEquals(TenantStatus.CLOSED, fromSuspended.getStatus());
        assertNull(fromSuspended.getSuspendedTime(), "only the closed stamp survives");
    }

    @Test
    @DisplayName("a closed tenant cannot be reactivated — closing is not a suspension")
    void activate_rejectedFromClosed() {
        // CLOSED is terminal by design; reviving a closed tenant is a provisioning decision, not a status
        // toggle. Silently allowing it would resurrect a workspace whose data was meant to stop being served.
        TenantInfoServiceImpl svc = serviceFor(tenant(TenantStatus.CLOSED));

        assertThrows(IllegalArgumentException.class, () -> svc.activate(1L));
    }

    @Test
    @DisplayName("re-running a transition already in effect writes nothing")
    void transitionToCurrentStatus_isANoOp() {
        // These are reachable from an MQ consumer and from ops double-clicking. A no-op rather than an error:
        // the caller asked for a state the tenant is already in, and got it.
        TenantInfo tenant = tenant(TenantStatus.ACTIVE);
        TenantInfoServiceImpl svc = serviceFor(tenant);

        svc.activate(1L);

        verify(svc, never()).updateOne(any(TenantInfo.class));
        verify(cacheService, never()).clear(anyString());
    }

    @Test
    @DisplayName("the platform tenant's status cannot be changed by any of the three")
    void platformTenant_isNotTransitionable() {
        // Suspending or closing it locks the platform admins out permanently — they could not log in to undo
        // it, and there is no other path back. Checked before the row is even loaded.
        TenantInfoServiceImpl svc = serviceFor(tenant(TenantStatus.ACTIVE));

        assertThrows(IllegalArgumentException.class, () -> svc.deactivate(PLATFORM_TENANT_ID));
        assertThrows(IllegalArgumentException.class, () -> svc.close(PLATFORM_TENANT_ID));
        assertThrows(IllegalArgumentException.class, () -> svc.activate(PLATFORM_TENANT_ID));
    }

    /** The tenant id the platform itself runs under, mirrored from the impl's own private constant. */
    private static final Long PLATFORM_TENANT_ID = -1L;

    private CacheService cacheService;

    /** A service whose {@code getById(1L)} returns this tenant, with the write and the cache stubbed out. */
    private TenantInfoServiceImpl serviceFor(TenantInfo tenant) {
        TenantInfoServiceImpl svc = Mockito.spy(new TenantInfoServiceImpl());
        cacheService = mock(CacheService.class);
        ReflectionTestUtils.setField(svc, "cacheService", cacheService);
        doReturn(Optional.of(tenant)).when(svc).getById(1L);
        doReturn(true).when(svc).updateOne(any(TenantInfo.class));
        return svc;
    }
}
