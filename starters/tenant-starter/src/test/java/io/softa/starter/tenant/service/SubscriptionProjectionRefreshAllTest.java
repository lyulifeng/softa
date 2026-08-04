package io.softa.starter.tenant.service;

import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.impl.SubscriptionProjectionServiceImpl;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The batch sweep the hourly cron drives: which tenants it rewrites, which it skips, and that it reads in
 * batches rather than per tenant.
 *
 * <p>Per-tenant arithmetic is covered by {@code SubscriptionProjectionServiceImplTest}. What only shows up in
 * the batch is the shape of the reads and the blast radius of one bad row: this is the sweep that keeps
 * untrafficked tenants current, so a single tenant pointing at a missing subscription must not stop the rest —
 * skipping the whole sweep would mean no downgrade ever emits its entitlement-changed event, and over-entitled
 * role grants would never be cleaned up.
 */
class SubscriptionProjectionRefreshAllTest {

    private static final LocalDate TODAY = LocalDate.now(ZoneOffset.UTC);

    private ModelService<?> modelService;
    private TenantSubscriptionService subscriptionService;
    private SubscriptionProjectionServiceImpl service;
    private List<TenantSubscription> subscriptions;
    private List<TenantSubscriptionPeriod> periods;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        modelService = mock(ModelService.class);
        subscriptionService = mock(TenantSubscriptionService.class);
        service = new SubscriptionProjectionServiceImpl(modelService, subscriptionService,
                mock(ApplicationEventPublisher.class));

        subscriptions = new ArrayList<>();
        periods = new ArrayList<>();

        when(subscriptionService.searchList(any(FlexQuery.class))).thenAnswer(inv -> subscriptions);
        when(subscriptionService.updateProjection(any(TenantSubscription.class))).thenReturn(true);
        when(modelService.searchList(anyString(), any(FlexQuery.class), eq(TenantSubscriptionPeriod.class)))
                .thenAnswer(inv -> periods);
    }

    @Test
    @DisplayName("a tenant whose projection is stale is rewritten")
    void staleProjection_rewritten() {
        subscriptions.add(subscription(9001L, TODAY.minusDays(1)));

        assertThat(service.refreshAll(List.of(tenant(1L, 9001L)))).isEqualTo(1);
        verify(subscriptionService).updateProjection(any(TenantSubscription.class));
    }

    @Test
    @DisplayName("a tenant already projected for its own today is skipped")
    void currentProjection_skipped() {
        // The gate is what makes the hourly sweep cheap: most ticks find most tenants current. It compares
        // against the tenant's local date, not the server's, which is why the job runs hourly at all.
        subscriptions.add(subscription(9001L, TODAY));

        assertThat(service.refreshAll(List.of(tenant(1L, 9001L)))).isZero();
        verify(subscriptionService, times(0)).updateProjection(any(TenantSubscription.class));
    }

    @Test
    @DisplayName("a tenant pointing at a missing subscription is skipped without stopping the sweep")
    void missingSubscription_skippedNotFatal() {
        // One orphaned tenant is a data problem; a sweep that aborted on it would be an outage for every
        // other tenant's entitlement cleanup.
        subscriptions.add(subscription(9002L, TODAY.minusDays(1)));   // only the second tenant's row exists

        int rewritten = service.refreshAll(List.of(tenant(1L, 9001L), tenant(2L, 9002L)));

        assertThat(rewritten).as("the healthy tenant is still refreshed").isEqualTo(1);
    }

    @Test
    @DisplayName("tenants with no subscription id are filtered out before any query")
    void tenantsWithoutSubscription_noQuery() {
        assertThat(service.refreshAll(List.of(tenant(1L, null)))).isZero();
        assertThat(service.refreshAll(List.of())).isZero();
        assertThat(service.refreshAll(null)).isZero();

        verify(subscriptionService, times(0)).searchList(any(FlexQuery.class));
    }

    @Test
    @DisplayName("many tenants cost two reads, not two per tenant")
    void batchedReads_notPerTenant() {
        // The sweep runs hourly over every active tenant. Per-tenant reads here turn one tick into 2N
        // queries, which is the difference between a sweep that scales and one that has to be turned off.
        for (long i = 1; i <= 25; i++) {
            subscriptions.add(subscription(9000L + i, TODAY.minusDays(1)));
        }
        List<TenantInfo> tenants = new ArrayList<>();
        for (long i = 1; i <= 25; i++) {
            tenants.add(tenant(i, 9000L + i));
        }

        assertThat(service.refreshAll(tenants)).isEqualTo(25);

        verify(subscriptionService, times(1)).searchList(any(FlexQuery.class));
        verify(modelService, times(1))
                .searchList(anyString(), any(FlexQuery.class), eq(TenantSubscriptionPeriod.class));
    }

    @Test
    @DisplayName("a period covering today is projected onto the tenant it belongs to")
    void periodsAreMatchedToTheirOwnSubscription() {
        // Both tenants are swept from one shared period read, so a grouping mistake would give one tenant
        // another's plan — the worst possible outcome for a read the authorization layer trusts.
        subscriptions.add(subscription(9001L, TODAY.minusDays(1)));
        subscriptions.add(subscription(9002L, TODAY.minusDays(1)));
        periods.add(period(1L, 9001L, "plan.pro"));
        periods.add(period(2L, 9002L, "plan.enterprise"));

        service.refreshAll(List.of(tenant(1L, 9001L), tenant(2L, 9002L)));

        assertThat(subscriptions.get(0).getPlanId()).isEqualTo("plan.pro");
        assertThat(subscriptions.get(1).getPlanId()).isEqualTo("plan.enterprise");
    }

    private TenantInfo tenant(Long id, Long subscriptionId) {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(id);
        tenant.setName("Tenant " + id);
        tenant.setSubscriptionId(subscriptionId);
        // UTC so "the tenant's today" is the fixture's TODAY regardless of where the suite runs.
        tenant.setDefaultTimezone(Timezone.UTC_P_00_00);
        return tenant;
    }

    private TenantSubscription subscription(Long id, LocalDate projectedForDate) {
        TenantSubscription sub = new TenantSubscription();
        sub.setId(id);
        sub.setProjectedForDate(projectedForDate);
        when(subscriptionService.getById(id)).thenReturn(Optional.of(sub));
        return sub;
    }

    private TenantSubscriptionPeriod period(Long id, Long subscriptionId, String planId) {
        TenantSubscriptionPeriod period = new TenantSubscriptionPeriod();
        period.setId(id);
        period.setSubscriptionId(subscriptionId);
        period.setPlanId(planId);
        period.setPeriodType(SubscriptionPeriodType.PAID);
        period.setEffectiveStartDate(TODAY.minusDays(30));
        period.setEffectiveEndDate(TODAY.plusDays(30));
        return period;
    }
}
