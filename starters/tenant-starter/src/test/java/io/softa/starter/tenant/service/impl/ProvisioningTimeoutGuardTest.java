package io.softa.starter.tenant.service.impl;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.tenant.config.TenantProvisioningProperties;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSeedProgress;
import io.softa.starter.tenant.enums.SeederStatus;
import io.softa.starter.tenant.enums.TenantProvisioningStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The provisioning timeout guard — the only thing that turns "this tenant never finished setting up" into a
 * state anyone can see.
 *
 * <p>What it measures is the whole point. Elapsed-since-creation answers the wrong question: a large tenant on
 * a busy cluster can legitimately run past the timeout while completing one seeder after another, and marking
 * it FAILED mid-flight showed ops a broken tenant that then silently un-broke itself. A state that cries wolf
 * gets ignored, and then the genuinely stuck tenant is ignored with it. So the anchor is the last sign of
 * progress, and these cases pin the difference between slow and stalled.
 */
class ProvisioningTimeoutGuardTest {

    private static final long TIMEOUT_SECONDS = 600;
    private static final LocalDateTime NOW = LocalDateTime.now();
    /** Comfortably outside the window. */
    private static final LocalDateTime LONG_AGO = NOW.minusSeconds(TIMEOUT_SECONDS * 3);
    /** Comfortably inside it. */
    private static final LocalDateTime JUST_NOW = NOW.minusSeconds(5);

    private TenantInfoServiceImpl tenantInfoService;
    private TenantProvisioningStatusService service;
    private List<TenantInfo> initializing;
    private List<TenantSeedProgress> progress;

    @BeforeEach
    void setUp() {
        tenantInfoService = mock(TenantInfoServiceImpl.class);
        TenantProvisioningProperties props = new TenantProvisioningProperties();
        props.setReadyTimeoutSeconds(TIMEOUT_SECONDS);

        initializing = new ArrayList<>();
        progress = new ArrayList<>();

        when(tenantInfoService.searchList(any(Filters.class))).thenAnswer(inv -> initializing);
        service = spy(new TenantProvisioningStatusService(tenantInfoService, props));
        doReturn(progress).when(service).searchList(any(Filters.class));
    }

    @Test
    @DisplayName("a tenant that has not moved since creation is stalled")
    void noProgressSinceCreation_failed() {
        // Nothing ever reported — the seed never started. Falls back to createdTime, the only signal available.
        initializing.add(tenant(1L, LONG_AGO));

        assertThat(service.failTimedOut()).isEqualTo(1);
        verify(tenantInfoService).markProvisioningStatus(1L, TenantProvisioningStatus.FAILED);
    }

    @Test
    @DisplayName("a slow tenant still reporting progress is left alone")
    void slowButProgressing_notFailed() {
        // Created long ago — over the timeout on the old elapsed-time measure — but a seeder reported seconds
        // ago. This is the false positive the guard used to produce.
        initializing.add(tenant(1L, LONG_AGO));
        progress.add(progressRow(1L, "pre-data", JUST_NOW));

        assertThat(service.failTimedOut()).isZero();
        verify(tenantInfoService, never()).markProvisioningStatus(any(), any());
    }

    @Test
    @DisplayName("a tenant whose last progress is itself stale is stalled")
    void progressedThenStopped_failed() {
        // The realistic failure: one seeder landed, the next one's consumer is down. Elapsed time alone cannot
        // tell this apart from the case above.
        initializing.add(tenant(1L, LONG_AGO));
        progress.add(progressRow(1L, "pre-data", LONG_AGO));

        assertThat(service.failTimedOut()).isEqualTo(1);
        verify(tenantInfoService).markProvisioningStatus(1L, TenantProvisioningStatus.FAILED);
    }

    @Test
    @DisplayName("the newest report wins, not the oldest")
    void newestProgressWins() {
        // Several seeders have reported at different times. Taking the earliest would fail a tenant that is
        // demonstrably still working.
        initializing.add(tenant(1L, LONG_AGO));
        progress.add(progressRow(1L, "pre-data", LONG_AGO));
        progress.add(progressRow(1L, "corehr", JUST_NOW));

        assertThat(service.failTimedOut()).isZero();
    }

    @Test
    @DisplayName("one stalled tenant does not drag a healthy one down with it")
    void perTenantDecision() {
        // The guard sweeps every INITIALIZING tenant in one pass, so the decision has to be per tenant. Each
        // tenant's progress is stubbed as its own answer — consecutively, in sweep order — because a stub that
        // returned every row to every tenant could not tell a per-tenant lookup from a global maximum, which is
        // exactly the bug this guards against.
        initializing.add(tenant(1L, LONG_AGO));
        initializing.add(tenant(2L, LONG_AGO));
        doReturn(List.of(progressRow(1L, "pre-data", JUST_NOW)))   // tenant 1 is moving
                .doReturn(List.of(progressRow(2L, "pre-data", LONG_AGO)))   // tenant 2 stopped
                .when(service).searchList(any(Filters.class));

        assertThat(service.failTimedOut()).isEqualTo(1);

        verify(tenantInfoService, never()).markProvisioningStatus(1L, TenantProvisioningStatus.FAILED);
        verify(tenantInfoService).markProvisioningStatus(2L, TenantProvisioningStatus.FAILED);
    }

    @Test
    @DisplayName("no INITIALIZING tenants — nothing swept, nothing written")
    void nothingInitializing_noOp() {
        assertThat(service.failTimedOut()).isZero();
        verify(tenantInfoService, never()).markProvisioningStatus(any(), any());
    }

    private static TenantInfo tenant(Long id, LocalDateTime createdTime) {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setProvisioningStatus(TenantProvisioningStatus.INITIALIZING);
        tenant.setCreatedTime(createdTime);
        return tenant;
    }

    private static TenantSeedProgress progressRow(Long tenantId, String seederKey, LocalDateTime at) {
        TenantSeedProgress row = new TenantSeedProgress();
        row.setTenantId(tenantId);
        row.setSeederKey(seederKey);
        row.setStatus(SeederStatus.DONE);
        row.setUpdatedTime(at);
        return row;
    }
}
