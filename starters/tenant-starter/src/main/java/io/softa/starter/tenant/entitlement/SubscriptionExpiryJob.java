package io.softa.starter.tenant.entitlement;

import java.time.LocalDate;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import static io.softa.framework.base.context.ContextUtils.inSystemContext;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.TenantLifecycle;
import io.softa.starter.tenant.service.TenantSubscriptionService;

/**
 * Time-driven subscription lifecycle transitions (版本计费). On a schedule it does two symmetric lifecycle
 * passes (each firing at the owning tenant's local midnight, then publishing {@link TenantEntitlementChangedEvent}
 * for the tenant — evict {@code entl:} + MQ role-grant cleanup via {@link EntitlementChangedListener}), plus a
 * reminder pass:
 * <ul>
 *   <li><b>Activate</b> — a {@link TenantLifecycle#SCHEDULED} subscription whose {@code effectiveFrom}
 *       has arrived (in the tenant's timezone) flips to {@link TenantLifecycle#SUBSCRIBED}.</li>
 *   <li><b>Expire</b> — an active subscription whose {@code effectiveTo} has passed flips to
 *       {@link TenantLifecycle#EXPIRED} (degrades to the fallback plan).</li>
 *   <li><b>Remind</b> — an active subscription a configured number of days from its {@code effectiveTo}
 *       (default 7 and 1) publishes {@link SubscriptionExpiryReminderEvent} once per tenant-local day, at or
 *       after the tenant-local reminder hour (default 10:00) → MQ → a user/business module emails the
 *       tenant's admins. Non-transitional; deduped via {@link TenantSubscription#getLastReminderDate()}.</li>
 * </ul>
 * This wires the subscription's <b>effective dates</b> to the {@code lifecycle} gate the resolver reads:
 * a row's life is {@code SCHEDULED →(effectiveFrom)→ SUBSCRIBED →(effectiveTo)→ EXPIRED}. The dates
 * drive the transitions; the resolver only ever reads {@code lifecycle} (no per-request date compare).
 *
 * <h3>Scheduling — cron-starter, hourly, per-tenant local midnight</h3>
 * The trigger is NOT here. A {@code sys_cron} row named {@code SubscriptionExpiry}
 * ({@code tenantJobMode=CrossTenant}, hourly) drives corehr's {@code SubscriptionExpiryCronHandler},
 * which calls {@link #syncDueTransitions()} — same job-in-starter / handler-in-corehr split as
 * {@code DynamicRoleSyncJob}. A single once-daily cron cannot hit every tenant's local midnight
 * (tenants span 24 UTC hours), so the row ticks hourly and each subscription transitions only once its
 * <b>local</b> date (in {@link TenantInfo#getDefaultTimezone()}) reaches {@code effectiveFrom} /
 * passes {@code effectiveTo} — the flip lands within an hour of that tenant's local midnight, the same
 * idiom as corehr's {@code SweepEmployeeStatus} / {@code RecomputeSnapshotStats}.
 *
 * <p>Runs in a system context (cross-tenant, permission-skipped) — subscriptions are shared,
 * non-tenant-scoped rows. Idempotent: lifecycle flips are one-shot and reminders are deduped per local day,
 * and rows are processed independently so one failure doesn't abort the sweep (concurrent cluster ticks are
 * therefore harmless).
 */
@Slf4j
@Service
public class SubscriptionExpiryJob {

    private final TenantSubscriptionService subscriptionService;
    private final ModelService<?> modelService;
    private final ApplicationEventPublisher eventPublisher;
    /** Tenant-local hour (0-23) at or after which expiry reminders go out (default 10 = 10 AM). */
    private final int reminderHour;
    /** Days-before-{@code effectiveTo} at which to remind (default 7 and 1; 0 = on the last day). */
    private final Set<Integer> reminderDaysBefore;

    public SubscriptionExpiryJob(TenantSubscriptionService subscriptionService,
                                 ModelService<?> modelService,
                                 ApplicationEventPublisher eventPublisher,
                                 @Value("${tenant.subscription.reminder.hour:10}") int reminderHour,
                                 @Value("${tenant.subscription.reminder.days-before:7,1}") Set<Integer> reminderDaysBefore) {
        this.subscriptionService = subscriptionService;
        this.modelService = modelService;
        this.eventPublisher = eventPublisher;
        this.reminderHour = reminderHour;
        this.reminderDaysBefore = reminderDaysBefore;
    }

    /**
     * Cron entry: activate scheduled subscriptions whose start date arrived, then expire active ones whose
     * end date passed, then send due expiry reminders — each in the owning tenant's timezone. Expire + remind
     * share one {@code effectiveTo} candidate query and one owner batch-load. Returns the number transitioned
     * (reminders are notifications, not transitions — not counted). Called by corehr's cron handler.
     */
    public int syncDueTransitions() {
        return inSystemContext(() -> {
            int activated = activateDue();
            // Expire + remind both key on effectiveTo — one candidate query + one owner batch-load serve both.
            LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).plusDays(maxReminderDaysBefore() + 1L);
            List<TenantSubscription> byEndDate = subscriptionService.searchList(
                    new Filters().le(TenantSubscription::getEffectiveTo, cutoff));
            Map<Long, TenantInfo> owners = ownersOf(byEndDate);
            int expired = expireDue(byEndDate, owners);
            remindUpcoming(byEndDate, owners);
            return activated + expired;
        });
    }

    /** Flip SCHEDULED subscriptions whose {@code effectiveFrom} has arrived (tenant-local) to SUBSCRIBED. */
    int activateDue() {
        // Coarse DB pre-filter: only rows whose effectiveFrom is at/before tomorrow (UTC) can be due
        // today in ANY timezone; the exact per-tenant check is below.
        LocalDate cutoff = LocalDate.now(ZoneOffset.UTC).plusDays(1);
        List<TenantSubscription> candidates = subscriptionService.searchList(
                new Filters().le(TenantSubscription::getEffectiveFrom, cutoff));
        Map<Long, TenantInfo> owners = ownersOf(candidates);
        int activated = 0;
        for (TenantSubscription sub : candidates) {
            if (sub.getLifecycle() != TenantLifecycle.SCHEDULED || sub.getEffectiveFrom() == null) {
                continue;   // only SCHEDULED rows activate
            }
            try {
                TenantInfo owner = owners.get(sub.getId());
                if (LocalDate.now(zoneOf(owner)).isBefore(sub.getEffectiveFrom())) {
                    continue;   // its local start date hasn't arrived yet
                }
                sub.setLifecycle(TenantLifecycle.SUBSCRIBED);
                subscriptionService.updateOne(sub);
                publishForOwner(owner, sub.getId(), "activated");
                activated++;
            } catch (RuntimeException ex) {
                log.error("Subscription lifecycle — failed to activate subscription {}", sub.getId(), ex);
            }
        }
        if (activated > 0) {
            log.info("Subscription lifecycle — activated {} scheduled subscription(s)", activated);
        }
        return activated;
    }

    /** Flip active subscriptions whose {@code effectiveTo} has passed (tenant-local) to EXPIRED. */
    int expireDue(List<TenantSubscription> candidates, Map<Long, TenantInfo> owners) {
        int expired = 0;
        for (TenantSubscription sub : candidates) {
            if (sub.getLifecycle() == null || !sub.getLifecycle().isEntitlementActive()
                    || sub.getEffectiveTo() == null) {
                continue;   // only active rows expire (skip SCHEDULED / already-EXPIRED)
            }
            try {
                TenantInfo owner = owners.get(sub.getId());
                if (!LocalDate.now(zoneOf(owner)).isAfter(sub.getEffectiveTo())) {
                    continue;   // not yet the day after effectiveTo in this tenant's timezone
                }
                sub.setLifecycle(TenantLifecycle.EXPIRED);
                subscriptionService.updateOne(sub);
                publishForOwner(owner, sub.getId(), "expired");
                expired++;
            } catch (RuntimeException ex) {
                log.error("Subscription lifecycle — failed to expire subscription {}", sub.getId(), ex);
            }
        }
        if (expired > 0) {
            log.info("Subscription lifecycle — expired {} lapsed subscription(s)", expired);
        }
        return expired;
    }

    /**
     * Publish an expiry reminder for each active subscription that is a configured number of days from its
     * {@code effectiveTo}, once per tenant-local day (at or after the tenant-local reminder hour). Fires
     * {@link SubscriptionExpiryReminderEvent} per due tenant → MQ → a user/business module emails the
     * tenant's admins, and stamps {@code lastReminderDate} so a later tick the same local day (misfire
     * catch-up, manual run) does not re-send. Returns the number of reminders published.
     */
    int remindUpcoming(List<TenantSubscription> candidates, Map<Long, TenantInfo> owners) {
        if (reminderDaysBefore == null || reminderDaysBefore.isEmpty()) {
            return 0;   // reminders disabled
        }
        int reminded = 0;
        for (TenantSubscription sub : candidates) {
            if (sub.getLifecycle() == null || !sub.getLifecycle().isEntitlementActive()
                    || sub.getEffectiveTo() == null) {
                continue;   // only active subscriptions with an end date get expiry reminders
            }
            try {
                TenantInfo owner = owners.get(sub.getId());
                if (owner == null || owner.getId() == null) {
                    continue;
                }
                ZonedDateTime localNow = ZonedDateTime.now(zoneOf(owner));
                Integer daysLeft = dueReminderDays(sub.getEffectiveTo(), localNow.toLocalDate(),
                        localNow.getHour(), sub.getLastReminderDate());
                if (daysLeft == null) {
                    continue;   // before the reminder hour, already reminded today, or not a days-before point
                }
                sub.setLastReminderDate(localNow.toLocalDate());   // stamp first → idempotent within the day
                subscriptionService.updateOne(sub);
                eventPublisher.publishEvent(new SubscriptionExpiryReminderEvent(
                        owner.getId(), owner.getName(), sub.getPlanId(), sub.getEffectiveTo(), daysLeft,
                        sub.getLifecycle() == TenantLifecycle.TRIAL));
                reminded++;
            } catch (RuntimeException ex) {
                log.error("Subscription lifecycle — failed to remind for subscription {}", sub.getId(), ex);
            }
        }
        if (reminded > 0) {
            log.info("Subscription lifecycle — published {} expiry reminder(s)", reminded);
        }
        return reminded;
    }

    /**
     * Pure reminder decision: at or after the tenant-local {@code reminderHour}, and not already reminded
     * this local day, return the whole days remaining until {@code effectiveTo} when that count is one of the
     * configured {@code reminderDaysBefore} points; otherwise {@code null} (no reminder). Extracted so the
     * day/hour/dedup logic is unit-testable without a clock.
     */
    Integer dueReminderDays(LocalDate effectiveTo, LocalDate localToday, int localHour, LocalDate lastReminderDate) {
        if (localHour < reminderHour || localToday.equals(lastReminderDate)) {
            return null;   // before today's reminder hour, or already reminded today (once-per-local-day)
        }
        long daysLeft = ChronoUnit.DAYS.between(localToday, effectiveTo);
        return (daysLeft >= 0 && reminderDaysBefore.contains((int) daysLeft)) ? (int) daysLeft : null;
    }

    /** Widest reminder window in days (0 when reminders are disabled). */
    private int maxReminderDaysBefore() {
        return (reminderDaysBefore == null || reminderDaysBefore.isEmpty())
                ? 0 : reminderDaysBefore.stream().mapToInt(Integer::intValue).max().orElse(0);
    }

    /** Reuse the entitlement-changed chain (evict entl: + MQ role cleanup) for the owning tenant. */
    private void publishForOwner(TenantInfo owner, Long subscriptionId, String action) {
        if (owner != null && owner.getId() != null) {
            eventPublisher.publishEvent(new TenantEntitlementChangedEvent(owner.getId()));
        } else {
            log.warn("Subscription lifecycle — {} orphan subscription {} (no owning tenant)",
                    action, subscriptionId);
        }
    }

    /**
     * Batch-load the owning tenants for a candidate set in one query, keyed by their {@code subscriptionId}
     * (the flipped owner FK {@code TenantInfo.subscriptionId} → subscription id). Avoids an N+1 per-row
     * {@code TenantInfo} lookup across the passes.
     */
    private Map<Long, TenantInfo> ownersOf(List<TenantSubscription> subscriptions) {
        List<Long> subscriptionIds = subscriptions.stream()
                .map(TenantSubscription::getId).filter(Objects::nonNull).toList();
        if (subscriptionIds.isEmpty()) {
            return Map.of();
        }
        List<TenantInfo> tenants = modelService.searchList("TenantInfo",
                new FlexQuery(new Filters().in(TenantInfo::getSubscriptionId, subscriptionIds)), TenantInfo.class);
        Map<Long, TenantInfo> bySubscriptionId = new HashMap<>();
        for (TenantInfo tenant : tenants) {
            if (tenant.getSubscriptionId() != null) {
                bySubscriptionId.put(tenant.getSubscriptionId(), tenant);
            }
        }
        return bySubscriptionId;
    }

    /** The owning tenant's zone (its {@code defaultTimezone}); UTC when there is no tenant / no zone. */
    private static ZoneId zoneOf(TenantInfo tenant) {
        return Timezone.zoneIdOrUtc(tenant == null ? null : tenant.getDefaultTimezone());
    }
}
