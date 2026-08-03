package io.softa.starter.tenant.entitlement;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.service.SubscriptionProjectionService;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyCollection;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * When a reminder is due: the tenant-local hour gate, the once-per-local-day gate, and which days-remaining
 * counts are reminder points.
 *
 * <p>These three rules decide whether any reminder is ever sent, and every way they can break is silent. A
 * reversed hour comparison either mails every tenant admin once an hour or never mails anyone; a dedup that
 * keys off the wrong date does the same. Nothing downstream would notice — the projection stays correct and
 * the job keeps reporting success.
 *
 * <p>{@code dueReminderDays} was extracted from the loop precisely so these rules could be checked without a
 * clock, and then went untested when the surrounding job was rewritten for the multi-period model; the cases
 * below are the ones the retired {@code SubscriptionExpiryJobTest} covered.
 */
class SubscriptionReminderGateTest {

    /** Tenant-local hour the fixture job reminds at. Both sides of it are exercised below. */
    private static final int REMINDER_HOUR = 10;
    private static final LocalDate TODAY = LocalDate.of(2026, 7, 31);

    // ─── the tenant-local hour gate ───

    @Test
    @DisplayName("before the tenant's reminder hour — nothing, even on a due day")
    void beforeReminderHour_notYet() {
        // The point of the hour is that mail arrives during the tenant's working day. An earlier tick must
        // decline rather than send, and must not consume the day either — see the following case.
        assertThat(job().dueReminderDays(TODAY.plusDays(7), TODAY, REMINDER_HOUR - 1, null)).isNull();
    }

    @Test
    @DisplayName("exactly at the reminder hour — due (the bound is inclusive)")
    void atReminderHour_due() {
        // An exclusive bound here would push every reminder to the next hour, which on the last configured
        // day (daysLeft = 0) means it is never sent at all.
        assertThat(job().dueReminderDays(TODAY.plusDays(7), TODAY, REMINDER_HOUR, null)).isEqualTo(7);
    }

    @Test
    @DisplayName("after the reminder hour — still due, so a missed tick is recoverable")
    void afterReminderHour_due() {
        // The job runs hourly and can miss a tick (deploy, outage). Later hours have to remain eligible or a
        // single missed tick loses that day's reminder permanently.
        assertThat(job().dueReminderDays(TODAY.plusDays(1), TODAY, 23, null)).isEqualTo(1);
    }

    // ─── the once-per-local-day gate ───

    @Test
    @DisplayName("already reminded today — silent for the rest of the day")
    void alreadyRemindedToday_silent() {
        // Without this, an hourly job sends up to fourteen identical mails per due day.
        assertThat(job().dueReminderDays(TODAY.plusDays(7), TODAY, REMINDER_HOUR, TODAY)).isNull();
    }

    @Test
    @DisplayName("reminded yesterday — today's reminder still goes out")
    void remindedYesterday_dueAgain() {
        // The dedup is per day, not per period: {7, 1} means two mails, and a stamp that suppressed
        // everything after the first would drop the 1-day warning — the one that matters most.
        assertThat(job().dueReminderDays(TODAY.plusDays(1), TODAY, REMINDER_HOUR, TODAY.minusDays(1)))
                .isEqualTo(1);
    }

    // ─── which counts are reminder points ───

    @Test
    @DisplayName("a day that is not a configured point — nothing")
    void offConfiguredDays_silent() {
        // 5 days out with {7, 1} configured. Reminding on every remaining day would train admins to ignore
        // the mail.
        assertThat(job().dueReminderDays(TODAY.plusDays(5), TODAY, REMINDER_HOUR, null)).isNull();
    }

    @Test
    @DisplayName("the last day counts as 0 days left, and is a point when configured")
    void lastDay_dueWhenZeroConfigured() {
        SubscriptionProjectionJob job = job(Set.of(7, 1, 0));
        assertThat(job.dueReminderDays(TODAY, TODAY, REMINDER_HOUR, null))
                .as("the period is still in effect on its end date — inclusive bounds")
                .isEqualTo(0);
    }

    @Test
    @DisplayName("already ended — never reminded, however long ago it lapsed")
    void pastEndDate_neverReminded() {
        // Days remaining goes negative once the end date passes. Without the >= 0 guard, a period that ended
        // 7 days ago matches the 7-day point with a negated sign and an expired tenant gets "expires in 7
        // days" mail forever.
        SubscriptionProjectionJob job = job();
        assertThat(job.dueReminderDays(TODAY.minusDays(7), TODAY, REMINDER_HOUR, null)).isNull();
        assertThat(job.dueReminderDays(TODAY.minusDays(1), TODAY, REMINDER_HOUR, null)).isNull();
    }

    // ─── the off switch ───

    @Test
    @DisplayName("no configured days — reminders off, and no query is made")
    void noConfiguredDays_disabled() {
        // Asserted as "no interaction" rather than "returns 0": the early return is also what keeps a
        // reminders-off deployment from running two extra queries per tick, for every tenant, hourly.
        TenantSubscriptionPeriodService periodService = mock(TenantSubscriptionPeriodService.class);

        assertThat(job(Set.of(), periodService).remindUpcoming(List.of(tenant(9001L)))).isZero();
        assertThat(job(null, periodService).remindUpcoming(List.of(tenant(9001L)))).isZero();

        verifyNoInteractions(periodService);
    }

    @Test
    @DisplayName("no tenants, or none with a subscription — no query either")
    void nothingToRemind_noQuery() {
        TenantSubscriptionPeriodService periodService = mock(TenantSubscriptionPeriodService.class);
        SubscriptionProjectionJob job = job(Set.of(7, 1), periodService);

        assertThat(job.remindUpcoming(List.of())).isZero();
        // A tenant with no subscription id cannot be keyed by one; the map would be empty and the
        // period query would ask for `subscriptionId IN ()`.
        assertThat(job.remindUpcoming(List.of(tenant(null)))).isZero();

        verifyNoInteractions(periodService);
    }

    // ─── the cron entry point ───

    @Test
    @DisplayName("the sweep returns the projections it rewrote, and reminds in the same pass")
    void syncDueTransitions_refreshesThenReminds() {
        // The cron calls only this. Its return value is what gets logged as the tick's outcome, so a sweep
        // that refreshed nothing and one that failed to count must not look alike.
        SubscriptionProjectionService projectionService = mock(SubscriptionProjectionService.class);
        TenantSubscriptionPeriodService periodService = mock(TenantSubscriptionPeriodService.class);
        ModelService<?> modelService = mock(ModelService.class);
        when(modelService.searchList(anyString(), any(FlexQuery.class), eq(TenantInfo.class)))
                .thenReturn(List.of(tenant(9001L)));
        when(projectionService.refreshAll(anyCollection())).thenReturn(3);
        when(periodService.searchList(any(FlexQuery.class))).thenReturn(List.of());

        SubscriptionProjectionJob job = new SubscriptionProjectionJob(projectionService, periodService,
                modelService, mock(ApplicationEventPublisher.class), REMINDER_HOUR, Set.of(7, 1));

        assertThat(job.syncDueTransitions()).isEqualTo(3);
        // Reminders are notifications, not state changes, so they are deliberately not counted — but they do
        // have to run: nothing else in the system sends them. `atLeastOnce` rather than an exact count because
        // the reminder pass legitimately reads twice (the periods ending in the window, then the siblings it
        // needs to spot a successor), and pinning that number here would just make the test brittle.
        verify(periodService, atLeastOnce()).searchList(any(FlexQuery.class));
    }

    @Test
    @DisplayName("only ACTIVE tenants with a subscription are swept")
    void syncDueTransitions_sweepsActiveTenantsOnly() {
        // Suspended and closed tenants cannot log in, so nothing reads their projection and refreshing it
        // would be work for no reader. The filter is asserted through the query rather than the result
        // because that is where the cost is.
        SubscriptionProjectionService projectionService = mock(SubscriptionProjectionService.class);
        ModelService<?> modelService = mock(ModelService.class);
        when(modelService.searchList(anyString(), any(FlexQuery.class), eq(TenantInfo.class)))
                .thenReturn(List.of());

        SubscriptionProjectionJob job = new SubscriptionProjectionJob(projectionService,
                mock(TenantSubscriptionPeriodService.class), modelService,
                mock(ApplicationEventPublisher.class), REMINDER_HOUR, Set.of(7, 1));
        job.syncDueTransitions();

        ArgumentCaptor<FlexQuery> captor = ArgumentCaptor.forClass(FlexQuery.class);
        verify(modelService).searchList(eq("TenantInfo"), captor.capture(), eq(TenantInfo.class));
        assertThat(captor.getValue().getFilters().toString())
                .contains("status")
                .contains("subscriptionId");
    }

    private SubscriptionProjectionJob job() {
        return job(Set.of(7, 1));
    }

    private SubscriptionProjectionJob job(Set<Integer> daysBefore) {
        return job(daysBefore, mock(TenantSubscriptionPeriodService.class));
    }

    @SuppressWarnings("unchecked")
    private SubscriptionProjectionJob job(Set<Integer> daysBefore, TenantSubscriptionPeriodService periods) {
        return new SubscriptionProjectionJob(mock(SubscriptionProjectionService.class), periods,
                mock(ModelService.class), mock(ApplicationEventPublisher.class), REMINDER_HOUR, daysBefore);
    }

    private TenantInfo tenant(Long subscriptionId) {
        TenantInfo tenant = new TenantInfo();
        tenant.setId(1001L);
        tenant.setName("Acme Corp");
        tenant.setSubscriptionId(subscriptionId);
        return tenant;
    }
}
