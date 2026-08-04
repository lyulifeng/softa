package io.softa.starter.tenant.provisioning;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.service.impl.TenantInfoServiceImpl;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Restarting a tenant's setup: what this service clears itself, what it deliberately leaves to the seeders,
 * and when it refuses.
 *
 * <p>The line between those first two is the point. An earlier version swept the tenant's rows out of every
 * module's tables from here, which works in one process and breaks silently once a module becomes its own
 * service — the sweep still runs, still reports success, and misses everything that moved out. So business
 * data is each seeder's own to discard as it re-runs, and this service only clears the provisioning state it
 * owns.
 */
class TenantSeedPurgeServiceTest {

    private static final long TENANT = 1001L;

    private TenantInfoServiceImpl tenantInfoService;
    private TenantProvisioningStatusService statusService;
    private TenantSeedCleaner seedCleaner;
    private ApplicationEventPublisher eventPublisher;
    private TenantSeedPurgeService service;

    @BeforeEach
    void setUp() {
        tenantInfoService = mock(TenantInfoServiceImpl.class);
        statusService = mock(TenantProvisioningStatusService.class);
        seedCleaner = mock(TenantSeedCleaner.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(tenantInfoService.getById(TENANT)).thenReturn(Optional.of(tenant()));
        // Not provisioned — the only state a rebuild is allowed in.
        when(tenantInfoService.isTenantProvisioned(TENANT)).thenReturn(false);
        when(seedCleaner.clearModels(anyLong(), anyList())).thenReturn(Map.of("UserAccount", 1));

        service = new TenantSeedPurgeService(tenantInfoService, statusService, seedCleaner, eventPublisher);
    }

    // ─── what it clears, and what it does not ───

    @Test
    @DisplayName("it clears the provisioning state it owns — the progress ledger and the accounts")
    void clearsOnlyItsOwnProvisioningState() {
        service.rebuild(TENANT);

        assertThat(clearedModels()).containsExactly(
                "UserRoleRel", "UserInvitation", "UserProfile", "UserAccount", "TenantSeedProgress");
    }

    @Test
    @DisplayName("no business model is cleared from here — that is each seeder's own job")
    void neverClearsBusinessData() {
        // Naming them is the assertion: if a future change reaches into a module's tables from this service,
        // it breaks here rather than at the deployment where that module became its own process.
        service.rebuild(TENANT);

        assertThat(clearedModels()).doesNotContain(
                "Department", "CostCentre", "LegalEntity", "Employee", "TenantOptionItem", "SysPreData");
    }

    @Test
    @DisplayName("the tenant and its subscription always survive")
    void neverClearsTheTenantOrItsSubscription() {
        service.rebuild(TENANT);

        assertThat(clearedModels()).doesNotContain(
                "TenantInfo", "TenantSubscription", "TenantSubscriptionPeriod");
    }

    @Test
    @DisplayName("the progress ledger is cleared last but before the announcement")
    void clearsProgressBeforeAnnouncing() {
        // Leaving the previous run's DONE rows means the FIRST seeder to report satisfies the readiness check
        // on its own, and the tenant is announced READY while the others are still seeding.
        service.rebuild(TENANT);

        InOrder order = inOrder(seedCleaner, statusService, eventPublisher);
        order.verify(seedCleaner).clearModels(anyLong(), anyList());
        order.verify(statusService).beginProvisioning(TENANT);
        order.verify(eventPublisher).publishEvent(any(TenantProvisionedEvent.class));
    }

    // ─── the guard ───

    @Test
    @DisplayName("a tenant that finished provisioning is refused, and nothing happens")
    void refusesAProvisionedTenant() {
        // The safety argument for deleting at all is that nobody could have been inside the tenant yet; once it
        // is READY that no longer holds. The refusal lives here rather than in whatever calls it.
        when(tenantInfoService.isTenantProvisioned(TENANT)).thenReturn(true);

        assertThatThrownBy(() -> service.rebuild(TENANT)).hasMessageContaining("finished provisioning");

        verify(seedCleaner, never()).clearModels(anyLong(), anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    @DisplayName("an unknown tenant, or none at all, is refused before anything is touched")
    void refusesUnknownOrNullTenant() {
        when(tenantInfoService.getById(TENANT)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.rebuild(TENANT)).hasMessageContaining("not found");

        assertThatThrownBy(() -> service.rebuild(null)).hasMessageContaining("Tenant id is required");

        verify(seedCleaner, never()).clearModels(anyLong(), anyList());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── re-announcing ───

    @Test
    @DisplayName("the replayed event is the one provision() publishes, carrying the tenant's own identity")
    void replaysTheProvisionedEvent() {
        // Identical to provision()'s, so the seeders run again unchanged and this path grows no sequence of its
        // own to drift from the real one.
        service.rebuild(TENANT);

        ArgumentCaptor<TenantProvisionedEvent> captor = ArgumentCaptor.forClass(TenantProvisionedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT);
        assertThat(captor.getValue().code()).isEqualTo("acme");
        assertThat(captor.getValue().name()).isEqualTo("Acme Corp");
    }

    @Test
    @DisplayName("the announcement is marked as a rebuild, which is what lets seeders clear safely")
    void announcementIsFlaggedAsRebuild() {
        // The flag is the whole difference between a seeder discarding its previous output and a seeder
        // wrecking a working tenant. Both cases arrive as the same event, so without it a seeder that clears
        // would also clear on every ordinary redelivery — dropping and re-creating rows other chains already
        // reference. provision() sends false; only this path sends true.
        service.rebuild(TENANT);

        ArgumentCaptor<TenantProvisionedEvent> captor = ArgumentCaptor.forClass(TenantProvisionedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().rebuild()).isTrue();
    }

    @Test
    @DisplayName("nothing to clear — still re-announced, and the report is honest about it")
    void nothingToClear_stillReseeds() {
        when(seedCleaner.clearModels(anyLong(), anyList())).thenReturn(Map.of());

        assertThat(service.rebuild(TENANT)).isEmpty();
        verify(eventPublisher).publishEvent(any(TenantProvisionedEvent.class));
    }

    @SuppressWarnings("unchecked")
    private List<String> clearedModels() {
        ArgumentCaptor<List<String>> captor = ArgumentCaptor.forClass(List.class);
        verify(seedCleaner).clearModels(anyLong(), captor.capture());
        return captor.getValue();
    }

    private static TenantInfo tenant() {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(TENANT);
        tenant.setCode("acme");
        tenant.setName("Acme Corp");
        return tenant;
    }
}
