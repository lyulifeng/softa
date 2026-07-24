package io.softa.starter.tenant.entitlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.TenantLifecycle;
import io.softa.starter.tenant.service.TenantSubscriptionService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Unit tests for {@link SubscriptionExpiryJob}'s passes. The lifecycle tests drive one pass directly
 * ({@code expireDue} / {@code activateDue}) — expire/remind take their pre-loaded candidate list + owner map
 * (as {@code syncDueTransitions} supplies them), activate does its own query — exercising the in-memory
 * per-tenant-timezone date gate (±2-day margins keep assertions deterministic regardless of the JVM/test
 * timezone). The reminder tests exercise the pure {@code dueReminderDays} day/hour/dedup decision directly
 * (a clock-free seam) plus the disabled-config guard on {@code remindUpcoming}.
 */
class SubscriptionExpiryJobTest {

    private TenantSubscriptionService subscriptionService;
    private ModelService<?> modelService;
    private ApplicationEventPublisher eventPublisher;
    private SubscriptionExpiryJob job;

    @BeforeEach
    void setUp() {
        subscriptionService = mock(TenantSubscriptionService.class);
        modelService = mock(ModelService.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        job = new SubscriptionExpiryJob(subscriptionService, modelService, eventPublisher, 10, Set.of(7, 1));
    }

    // ─── expire pass (candidates + owner map supplied, as syncDueTransitions does) ───

    @Test
    void expiresActiveLapsedSub_flipsAndPublishes() {
        List<TenantSubscription> candidates = List.of(
                sub(10L, TenantLifecycle.SUBSCRIBED, null, LocalDate.now().minusDays(2)),   // lapsed
                sub(11L, TenantLifecycle.EXPIRED, null, LocalDate.now().minusDays(2)));      // not active → skip

        assertThat(job.expireDue(candidates, Map.of(10L, tenant(100L)))).isEqualTo(1);
        verify(subscriptionService).updateOne(argThat(s ->
                s.getId().equals(10L) && s.getLifecycle() == TenantLifecycle.EXPIRED));
        verify(subscriptionService, never()).updateOne(argThat(s -> s.getId().equals(11L)));
        verify(eventPublisher).publishEvent(new TenantEntitlementChangedEvent(100L));
    }

    @Test
    void notYetExpired_futureEffectiveTo_skips() {
        List<TenantSubscription> candidates = List.of(
                sub(15L, TenantLifecycle.SUBSCRIBED, null, LocalDate.now().plusDays(2)));

        assertThat(job.expireDue(candidates, Map.of(15L, tenant(150L)))).isZero();
        verify(subscriptionService, never()).updateOne(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void orphanExpiry_noOwner_expiresButNoEvent() {
        List<TenantSubscription> candidates = List.of(
                sub(20L, TenantLifecycle.SUBSCRIBED, null, LocalDate.now().minusDays(2)));

        assertThat(job.expireDue(candidates, Map.of())).isEqualTo(1);   // no owner → UTC fallback still expires
        verify(subscriptionService).updateOne(any(TenantSubscription.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── activate pass (does its own query + batch owner load) ───

    @Test
    void activatesScheduledSub_whenStartDateReached() {
        when(subscriptionService.searchList(any(Filters.class))).thenReturn(List.of(
                sub(30L, TenantLifecycle.SCHEDULED, LocalDate.now().minusDays(2), null)));   // start reached
        ownerIs(owning(30L, 300L, Timezone.UTC_P_08_00));

        assertThat(job.activateDue()).isEqualTo(1);
        verify(subscriptionService).updateOne(argThat(s ->
                s.getId().equals(30L) && s.getLifecycle() == TenantLifecycle.SUBSCRIBED));
        verify(eventPublisher).publishEvent(new TenantEntitlementChangedEvent(300L));
    }

    @Test
    void scheduledButStartInFuture_skips() {
        when(subscriptionService.searchList(any(Filters.class))).thenReturn(List.of(
                sub(31L, TenantLifecycle.SCHEDULED, LocalDate.now().plusDays(2), null)));
        ownerIs(owning(31L, 310L, Timezone.UTC_P_08_00));

        assertThat(job.activateDue()).isZero();
        verify(subscriptionService, never()).updateOne(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    @Test
    void nonScheduledSub_ignoredByActivation() {
        // The coarse effectiveFrom query can return active subs too — activation must skip non-SCHEDULED.
        when(subscriptionService.searchList(any(Filters.class))).thenReturn(List.of(
                sub(32L, TenantLifecycle.SUBSCRIBED, LocalDate.now().minusDays(2), null)));
        ownerIs(owning(32L, 320L, Timezone.UTC_P_08_00));

        assertThat(job.activateDue()).isZero();
        verify(subscriptionService, never()).updateOne(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── combined ───

    @Test
    void syncDueTransitions_noCandidates_zero() {
        when(subscriptionService.searchList(any(Filters.class))).thenReturn(List.of());

        assertThat(job.syncDueTransitions()).isZero();
        verify(subscriptionService, never()).updateOne(any());
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── reminder decision (dueReminderDays: pure hour/day/dedup gate, reminderHour=10, daysBefore={7,1}) ───

    @Test
    void remindsAtOrAfterLocalHour_onConfiguredDaysBefore() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        assertThat(job.dueReminderDays(today.plusDays(7), today, 10, null)).isEqualTo(7);   // exactly the hour
        assertThat(job.dueReminderDays(today.plusDays(1), today, 10, null)).isEqualTo(1);
        assertThat(job.dueReminderDays(today.plusDays(7), today, 15, null)).isEqualTo(7);   // later that day still sends
    }

    @Test
    void noReminder_offConfiguredDaysBefore() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        assertThat(job.dueReminderDays(today.plusDays(3), today, 10, null)).isNull();    // 3 not in {7,1}
        assertThat(job.dueReminderDays(today, today, 10, null)).isNull();                // 0 (last day) not default
        assertThat(job.dueReminderDays(today.minusDays(1), today, 10, null)).isNull();   // already past
    }

    @Test
    void noReminder_beforeLocalReminderHour() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        assertThat(job.dueReminderDays(today.plusDays(7), today, 9, null)).isNull();   // 9 < 10, too early
        assertThat(job.dueReminderDays(today.plusDays(7), today, 0, null)).isNull();
    }

    @Test
    void noReminder_alreadyRemindedThisLocalDay() {
        LocalDate today = LocalDate.of(2026, 1, 10);
        // idempotency: a later tick the same local day (misfire catch-up / manual run) must not re-send
        assertThat(job.dueReminderDays(today.plusDays(7), today, 10, today)).isNull();
        // but a stamp from a previous day does not block today's reminder
        assertThat(job.dueReminderDays(today.plusDays(7), today, 10, today.minusDays(1))).isEqualTo(7);
    }

    @Test
    void remindUpcoming_disabledWhenNoDaysConfigured() {
        SubscriptionExpiryJob noReminders = new SubscriptionExpiryJob(
                subscriptionService, modelService, eventPublisher, 10, Set.of());
        List<TenantSubscription> candidates = List.of(
                sub(40L, TenantLifecycle.SUBSCRIBED, null, LocalDate.now().plusDays(7)));

        assertThat(noReminders.remindUpcoming(candidates, Map.of(40L, tenant(400L)))).isZero();
        verify(eventPublisher, never()).publishEvent(any());
    }

    // ─── helpers ───

    private void ownerIs(TenantInfo... owner) {
        when(modelService.searchList(eq("TenantInfo"), any(FlexQuery.class), eq(TenantInfo.class)))
                .thenReturn(List.of(owner));
    }

    private static TenantSubscription sub(Long id, TenantLifecycle lifecycle,
                                          LocalDate effectiveFrom, LocalDate effectiveTo) {
        TenantSubscription s = new TenantSubscription();
        s.setId(id);
        s.setLifecycle(lifecycle);
        s.setEffectiveFrom(effectiveFrom);
        s.setEffectiveTo(effectiveTo);
        return s;
    }

    /** Tenant used as an owner-map value (keyed manually by the test); timezone UTC+08:00. */
    private static TenantInfo tenant(Long id) {
        TenantInfo t = new TenantInfo();
        t.setId(id);
        t.setDefaultTimezone(Timezone.UTC_P_08_00);
        return t;
    }

    /** Tenant returned by the batch owner query — carries {@code subscriptionId} so {@code ownersOf} keys it. */
    private static TenantInfo owning(Long subscriptionId, Long tenantId, Timezone timezone) {
        TenantInfo t = new TenantInfo();
        t.setId(tenantId);
        t.setSubscriptionId(subscriptionId);
        t.setDefaultTimezone(timezone);
        return t;
    }
}
