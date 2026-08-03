package io.softa.starter.tenant.provisioning;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.InOrder;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.SubscriptionStatus;
import io.softa.starter.tenant.enums.TenantStatus;
import io.softa.starter.tenant.service.SubscriptionPeriodPatch;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;
import io.softa.starter.tenant.service.TenantSubscriptionService;
import io.softa.starter.tenant.service.impl.TenantInfoServiceImpl;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Opening a tenant: the two rows, the order they have to be written in, and how a day-one sale is recorded.
 *
 * <p>Every customer goes through this once, and the failure modes are not gradual. If the subscription row is
 * missing, the tenant list and authorization both read a null projection. If the back-link is skipped, billing
 * cannot find its way from a subscription to its tenant. If the periods a sales rep entered on the create form
 * are dropped, the customer is created on the free plan having paid for Pro — and nothing reports an error,
 * because provisioning itself succeeded.
 *
 * <p>The class this replaces tested a subscription model that no longer exists (one interval and a lifecycle
 * column on the subscription row). What survives that rewrite is the sequencing, and that is what is pinned
 * here.
 */
class TenantProvisioningServiceTest {

    private static final long SUB_ID = 9001L;
    private static final long TENANT_ID = 1001L;

    private TenantInfoServiceImpl tenantInfoService;
    private TenantSubscriptionService subscriptionService;
    private TenantSubscriptionPeriodService periodService;
    private TenantProvisioningStatusService provisioningStatusService;
    private ApplicationEventPublisher eventPublisher;
    private TenantProvisioningService service;

    @BeforeEach
    void setUp() {
        tenantInfoService = mock(TenantInfoServiceImpl.class);
        subscriptionService = mock(TenantSubscriptionService.class);
        periodService = mock(TenantSubscriptionPeriodService.class);
        provisioningStatusService = mock(TenantProvisioningStatusService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        when(subscriptionService.createOne(any(TenantSubscription.class))).thenReturn(SUB_ID);
        when(tenantInfoService.createOne(any(TenantInfo.class))).thenReturn(TENANT_ID);
        when(tenantInfoService.getById(TENANT_ID)).thenReturn(Optional.of(provisionedTenant()));

        service = new TenantProvisioningService(tenantInfoService, subscriptionService, periodService,
                eventPublisher, provisioningStatusService);
    }

    // ─── the two rows ───

    @Test
    @DisplayName("every tenant is born with a subscription row, empty rather than absent")
    void subscriptionRowCreatedAtBirth() {
        service.provision(request(null));

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        verify(subscriptionService).createOne(captor.capture());
        // NEVER_SUBSCRIBED, no plan: a tenant is on the floor plan by birth, and "free is not recorded"
        // constrains the period table, not this row. A missing row instead of an empty one would leave the
        // tenant list and the entitlement resolver reading null.
        assertThat(captor.getValue().getSubscriptionStatus()).isEqualTo(SubscriptionStatus.NEVER_SUBSCRIBED);
        assertThat(captor.getValue().getPlanId()).isNull();
    }

    @Test
    @DisplayName("the subscription is created first so the tenant row can carry its id")
    void subscriptionBeforeTenant() {
        service.provision(request(null));

        // The framework's 1:1 puts the FK on the owner (TenantInfo.subscriptionId), so this order is forced —
        // reversing it would leave the tenant row with a null subscription.
        InOrder order = inOrder(subscriptionService, tenantInfoService);
        order.verify(subscriptionService).createOne(any(TenantSubscription.class));
        order.verify(tenantInfoService).createOne(any(TenantInfo.class));

        ArgumentCaptor<TenantInfo> captor = ArgumentCaptor.forClass(TenantInfo.class);
        verify(tenantInfoService).createOne(captor.capture());
        assertThat(captor.getValue().getSubscriptionId()).isEqualTo(SUB_ID);
        assertThat(captor.getValue().getStatus()).isEqualTo(TenantStatus.ACTIVE);
    }

    @Test
    @DisplayName("the back-link writes only tenantId, with nulls ignored")
    void backLinkIgnoresNulls() {
        service.provision(request(null));

        ArgumentCaptor<TenantSubscription> captor = ArgumentCaptor.forClass(TenantSubscription.class);
        // `true` = ignore nulls. The full-overwrite variant would carry the entity's virtual `periods`
        // relation as null, which the framework reads as "clear the relation" — the same mechanism that once
        // deleted every period a refresh was meant to describe.
        verify(subscriptionService).updateOne(captor.capture(), eq(true));
        assertThat(captor.getValue().getId()).isEqualTo(SUB_ID);
        assertThat(captor.getValue().getTenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().getPlanId())
                .as("anything else set here would be written over the row just created")
                .isNull();
    }

    // ─── the day-one sale ───

    @Test
    @DisplayName("periods from the create form go through the guarded patch entry point")
    void initialPeriodsRecordedThroughThePatch() {
        SubscriptionPeriodPatch patch = new SubscriptionPeriodPatch();
        patch.setCreate(List.of(new SubscriptionPeriodPatch.PeriodInput()));

        service.provision(request(patch));

        // Not the framework's nested-relation pipeline: that writes through the generic ModelService, which
        // runs none of the period guards and refreshes no projection, leaving the tenant list reading
        // NEVER_SUBSCRIBED for a customer who just bought Pro.
        verify(periodService).applyPatch(SUB_ID, patch);
    }

    @Test
    @DisplayName("periods are recorded after the tenant row exists, so the refresh can read its timezone")
    void periodsRecordedAfterTenantRow() {
        SubscriptionPeriodPatch patch = new SubscriptionPeriodPatch();
        patch.setCreate(List.of(new SubscriptionPeriodPatch.PeriodInput()));

        service.provision(request(patch));

        // The patch ends in a projection refresh, and that resolves "today" in the tenant's own zone. Running
        // it before the tenant row existed would date the projection in the server's zone instead.
        InOrder order = inOrder(tenantInfoService, periodService);
        order.verify(tenantInfoService).createOne(any(TenantInfo.class));
        order.verify(periodService).applyPatch(anyLong(), any(SubscriptionPeriodPatch.class));
    }

    @Test
    @DisplayName("no periods on the request — nothing recorded, and that is a normal outcome")
    void noPeriods_nothingRecorded() {
        // A tenant nobody has sold anything to yet simply has none and runs on the floor plan.
        service.provision(request(null));

        verify(periodService, never()).applyPatch(anyLong(), any(SubscriptionPeriodPatch.class));
    }

    @Test
    @DisplayName("a subscription block with no periods is not treated as an empty patch")
    void subscriptionInputWithoutPeriods_nothingRecorded() {
        ProvisionTenantRequest request = request(null);
        request.setSubscriptionId(new ProvisionTenantRequest.SubscriptionInput());

        service.provision(request);

        // An empty patch would still run and refresh; skipping is what keeps a plain create free of a
        // pointless write.
        verify(periodService, never()).applyPatch(anyLong(), any(SubscriptionPeriodPatch.class));
    }

    // ─── what the rest of the system is told ───

    @Test
    @DisplayName("the initialization axis is marked before the event, not after")
    void statusMarkedBeforeBroadcast() {
        service.provision(request(null));

        // Listeners of the event report their own seeder completion; if the axis were opened after they ran,
        // a fast seeder's report would arrive before anything was waiting for it and the tenant would sit in
        // INITIALIZING forever.
        InOrder order = inOrder(provisioningStatusService, eventPublisher);
        order.verify(provisioningStatusService).beginProvisioning(TENANT_ID);
        order.verify(eventPublisher).publishEvent(any(TenantProvisionedEvent.class));
    }

    @Test
    @DisplayName("the provisioned event carries the id and the normalized code")
    void eventCarriesIdAndCode() {
        service.provision(request(null));

        ArgumentCaptor<TenantProvisionedEvent> captor = ArgumentCaptor.forClass(TenantProvisionedEvent.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().tenantId()).isEqualTo(TENANT_ID);
        assertThat(captor.getValue().code()).isEqualTo("acme-corp");
    }

    // ─── the code slug ───

    @Test
    @DisplayName("a blank code is slugged from the name")
    void blankCode_sluggedFromName() {
        ProvisionTenantRequest request = request(null);
        request.setName("  Acme Corp (APAC)!  ");
        request.setCode(null);

        assertThat(service.provision(request).code()).isEqualTo("acme-corp-apac");
    }

    @Test
    @DisplayName("a name with nothing sluggable still yields a usable code")
    void unsluggableName_fallsBackRatherThanEmpty() {
        // An empty code would collide with every other empty code on the unique index, so the second such
        // tenant could never be created.
        ProvisionTenantRequest request = request(null);
        request.setName("...");
        request.setCode(null);

        assertThat(service.provision(request).code()).isEqualTo("tenant");
    }

    @Test
    @DisplayName("a supplied code wins over the name")
    void suppliedCodeWins() {
        ProvisionTenantRequest request = request(null);
        request.setCode("Acme_APAC");

        assertThat(service.provision(request).code()).isEqualTo("acme-apac");
    }

    // ─── fixtures ───

    private ProvisionTenantRequest request(SubscriptionPeriodPatch periods) {
        ProvisionTenantRequest request = new ProvisionTenantRequest();
        request.setName("Acme Corp");
        if (periods != null) {
            ProvisionTenantRequest.SubscriptionInput input = new ProvisionTenantRequest.SubscriptionInput();
            input.setPeriods(periods);
            request.setSubscriptionId(input);
        }
        return request;
    }

    /** The tenant as re-read after creation, which is where the period recording gets the subscription id. */
    private TenantInfo provisionedTenant() {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(TENANT_ID);
        tenant.setSubscriptionId(SUB_ID);
        return tenant;
    }
}
