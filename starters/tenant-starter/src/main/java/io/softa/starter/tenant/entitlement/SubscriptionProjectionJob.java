package io.softa.starter.tenant.entitlement;

import static io.softa.framework.base.context.ContextUtils.inSystemContext;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.enums.TenantStatus;
import io.softa.starter.tenant.service.SubscriptionProjectionService;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;

/**
 * Hourly sweep: refresh every tenant's subscription projection, then send the expiry reminders that fall
 * due. Triggered by the host app's cron dispatcher, not by {@code @Scheduled}, so the schedule is data.
 *
 * <p>Hourly rather than daily because "the day changed" happens at a different instant per tenant — each
 * tenant's own midnight. Within an hour of it, that tenant is swept.
 *
 * <h3>What this job is and is not responsible for</h3>
 * It does <b>not</b> own correctness. Readers repair a stale projection themselves (see
 * {@code SubscriptionProjectionService}), so an outage here does not serve the wrong plan. What only this
 * job can do is:
 *
 * <ul>
 *   <li><b>Refresh tenants nobody is looking at.</b> Read-time repair needs a reader; a tenant with no
 *       traffic would otherwise keep its stale projection — and, more importantly, would never emit the
 *       entitlement-changed event, so <b>over-entitled role grants would never be cleaned up</b> after a
 *       downgrade.</li>
 *   <li><b>Send expiry reminders.</b> There is no read path that would do this.</li>
 * </ul>
 *
 * Both consequences are silent, so {@code [CRON_FAILURE]} needs an alert rather than just a log line —
 * this codebase has already had a nightly job fail quietly for a long time unnoticed.
 *
 * <p>Runs in a system context (cross-tenant, permission-skipped): subscriptions and periods are shared,
 * non-tenant-scoped rows. Idempotent — the projection refresh is a no-op once current, and reminders are
 * deduped per tenant-local day, so overlapping cluster ticks are harmless.
 */
@Slf4j
@Service
public class SubscriptionProjectionJob {

    private final SubscriptionProjectionService projectionService;
    private final TenantSubscriptionPeriodService periodService;
    private final ModelService<?> modelService;
    private final ApplicationEventPublisher eventPublisher;
    /** Tenant-local hour (0-23) at or after which expiry reminders go out (default 10 = 10 AM). */
    private final int reminderHour;
    /** Days-before-end at which to remind (default 7 and 1; 0 = on the last day). */
    private final Set<Integer> reminderDaysBefore;

    public SubscriptionProjectionJob(SubscriptionProjectionService projectionService,
                                     TenantSubscriptionPeriodService periodService,
                                     ModelService<?> modelService,
                                     ApplicationEventPublisher eventPublisher,
                                     @Value("${tenant.subscription.reminder.hour:10}") int reminderHour,
                                     @Value("${tenant.subscription.reminder.days-before:7,1}")
                                     Set<Integer> reminderDaysBefore) {
        this.projectionService = projectionService;
        this.periodService = periodService;
        this.modelService = modelService;
        this.eventPublisher = eventPublisher;
        this.reminderHour = reminderHour;
        this.reminderDaysBefore = reminderDaysBefore;
    }

    /**
     * Cron entry. Returns the number of projections actually rewritten (reminders are notifications, not
     * state changes, so they are not counted).
     */
    public int syncDueTransitions() {
        return inSystemContext(() -> {
            List<TenantInfo> tenants = activeTenants();
            int refreshed = projectionService.refreshAll(tenants);
            if (refreshed > 0) {
                log.info("Subscription projection — refreshed {} of {} tenant(s)", refreshed, tenants.size());
            }
            remindUpcoming(tenants);
            return refreshed;
        });
    }

    /**
     * Publish an expiry reminder for each period that is a configured number of days from its end date,
     * once per tenant-local day at or after that tenant's reminder hour.
     *
     * <p>Note this is a <b>separate query</b> from the projection refresh: the refresh wants the period
     * covering today plus the next one, whereas this wants periods ending 7 or 1 days out. Only the owner
     * load is shared — that one is passed in rather than repeated.
     *
     * @return how many reminders were published
     */
    int remindUpcoming(List<TenantInfo> tenants) {
        if (reminderDaysBefore == null || reminderDaysBefore.isEmpty() || tenants.isEmpty()) {
            return 0;   // reminders disabled
        }
        Map<Long, TenantInfo> bySubscription = tenants.stream()
                .filter(t -> t.getSubscriptionId() != null)
                .collect(Collectors.toMap(TenantInfo::getSubscriptionId, Function.identity(), (a, b) -> a));
        if (bySubscription.isEmpty()) {
            return 0;
        }
        // Widest window, plus a day of slack so no tenant's local date falls outside it.
        LocalDate horizon = LocalDate.now().plusDays(maxReminderDaysBefore() + 1L);
        List<TenantSubscriptionPeriod> ending = periodService.searchList(new FlexQuery(
                Filters.of("subscriptionId", Operator.IN, List.copyOf(bySubscription.keySet()))
                        .and("effectiveEndDate", Operator.LESS_THAN_OR_EQUAL, horizon)));
        Map<Long, List<TenantSubscriptionPeriod>> allBySubscription = periodsOf(bySubscription.keySet());

        int reminded = 0;
        for (TenantSubscriptionPeriod period : ending) {
            try {
                TenantInfo owner = bySubscription.get(period.getSubscriptionId());
                if (owner == null || owner.getId() == null || period.getEffectiveEndDate() == null) {
                    continue;
                }
                ZonedDateTime localNow = ZonedDateTime.now(zoneOf(owner));
                Integer daysLeft = dueReminderDays(period.getEffectiveEndDate(), localNow.toLocalDate(),
                        localNow.getHour(), period.getLastReminderDate());
                if (daysLeft == null) {
                    continue;   // before the reminder hour, already reminded today, or not a due point
                }
                // Seamless successor → already renewed; saying "about to expire" would be wrong. A gap-
                // separated one still gets a reminder, but a different message — the coverage really does
                // lapse in between.
                LocalDate nextStart = successorStart(allBySubscription.get(period.getSubscriptionId()), period);
                boolean seamless = nextStart != null
                        && !nextStart.isAfter(period.getEffectiveEndDate().plusDays(1));
                if (seamless) {
                    continue;
                }
                period.setLastReminderDate(localNow.toLocalDate());   // stamp first → idempotent for the day
                periodService.updateOne(period);
                eventPublisher.publishEvent(new SubscriptionExpiryReminderEvent(
                        owner.getId(), owner.getName(), period.getPlanId(), period.getEffectiveEndDate(),
                        daysLeft, period.getPeriodType() == SubscriptionPeriodType.TRIAL, nextStart));
                reminded++;
            } catch (RuntimeException ex) {
                log.error("Subscription projection — failed to remind for period {}", period.getId(), ex);
            }
        }
        if (reminded > 0) {
            log.info("Subscription projection — published {} expiry reminder(s)", reminded);
        }
        return reminded;
    }

    /**
     * Pure reminder decision, extracted so the day / hour / dedup rules are unit-testable without a clock:
     * at or after the tenant-local reminder hour, not already reminded this local day, and the whole days
     * remaining is one of the configured points.
     */
    Integer dueReminderDays(LocalDate endDate, LocalDate localToday, int localHour,
                            LocalDate lastReminderDate) {
        if (localHour < reminderHour || localToday.equals(lastReminderDate)) {
            return null;
        }
        long daysLeft = ChronoUnit.DAYS.between(localToday, endDate);
        return (daysLeft >= 0 && reminderDaysBefore.contains((int) daysLeft)) ? (int) daysLeft : null;
    }

    /**
     * The earliest period that starts after this one ends, or null when nothing follows.
     *
     * <p>Returns the date rather than a boolean because the answer drives two different decisions: a
     * successor starting on or before the day after = already renewed, don't remind at all; a later one =
     * there is a real gap, and the reminder has to say so. An earlier version returned a boolean and threw
     * the date away, so a customer with a gap-separated renewal got the plain "please renew before it lapses"
     * — wrong for someone who has renewed, and its "ignore this if you already renewed" line made the one
     * message they needed to read the easiest to dismiss.
     */
    private LocalDate successorStart(List<TenantSubscriptionPeriod> siblings, TenantSubscriptionPeriod period) {
        if (siblings == null) {
            return null;
        }
        return siblings.stream()
                .filter(other -> !other.getId().equals(period.getId()))
                .map(TenantSubscriptionPeriod::getEffectiveStartDate)
                .filter(start -> start != null && start.isAfter(period.getEffectiveEndDate()))
                .min(LocalDate::compareTo)
                .orElse(null);
    }

    private int maxReminderDaysBefore() {
        return (reminderDaysBefore == null || reminderDaysBefore.isEmpty())
                ? 0 : reminderDaysBefore.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Only ACTIVE tenants are swept — suspended / closed ones cannot log in, so nothing reads them. */
    private List<TenantInfo> activeTenants() {
        return modelService.searchList("TenantInfo",
                new FlexQuery(Filters.of("status", Operator.EQUAL, TenantStatus.ACTIVE)
                        .and("subscriptionId", Operator.IS_SET, null)),
                TenantInfo.class);
    }

    private Map<Long, List<TenantSubscriptionPeriod>> periodsOf(Set<Long> subscriptionIds) {
        return periodService.searchList(new FlexQuery(
                        Filters.of("subscriptionId", Operator.IN, List.copyOf(subscriptionIds))))
                .stream().collect(Collectors.groupingBy(TenantSubscriptionPeriod::getSubscriptionId));
    }

    private ZoneId zoneOf(TenantInfo tenant) {
        return Timezone.zoneIdOrUtc(tenant == null ? null : tenant.getDefaultTimezone());
    }
}
