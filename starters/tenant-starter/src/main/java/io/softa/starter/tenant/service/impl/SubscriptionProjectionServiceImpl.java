package io.softa.starter.tenant.service.impl;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.stream.Collectors;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import io.softa.framework.base.enums.Timezone;
import io.softa.framework.base.enums.Operator;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entitlement.TenantEntitlementChangedEvent;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionStatus;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.SubscriptionProjectionService;
import io.softa.starter.tenant.service.TenantSubscriptionService;

/**
 * Default {@link SubscriptionProjectionService}. See the interface for why the projection may be
 * authorized against at all; this class is just the arithmetic plus the write.
 */
@Slf4j
@Service
public class SubscriptionProjectionServiceImpl implements SubscriptionProjectionService {

    private static final String PERIOD_MODEL = TenantSubscriptionPeriod.class.getSimpleName();

    private final ModelService<?> modelService;
    private final TenantSubscriptionService subscriptionService;
    private final ApplicationEventPublisher eventPublisher;

    public SubscriptionProjectionServiceImpl(ModelService<?> modelService,
                                            TenantSubscriptionService subscriptionService,
                                            ApplicationEventPublisher eventPublisher) {
        this.modelService = modelService;
        this.subscriptionService = subscriptionService;
        this.eventPublisher = eventPublisher;
    }

    @Override
    public LocalDate tenantLocalToday(TenantInfo tenant) {
        return LocalDate.now(Timezone.zoneIdOrUtc(tenant == null ? null : tenant.getDefaultTimezone()));
    }

    @Override
    @SkipPermissionCheck
    public TenantSubscription refresh(TenantInfo tenant) {
        return refresh(tenant, false);
    }

    @Override
    @SkipPermissionCheck
    public TenantSubscription refreshNow(TenantInfo tenant) {
        return refresh(tenant, true);
    }

    private TenantSubscription refresh(TenantInfo tenant, boolean force) {
        if (tenant == null || tenant.getSubscriptionId() == null) {
            return null;
        }
        TenantSubscription sub = subscriptionService.getById(tenant.getSubscriptionId()).orElse(null);
        if (sub == null) {
            log.warn("Subscription projection — tenant {} points at missing subscription {}",
                    tenant.getId(), tenant.getSubscriptionId());
            return null;
        }
        LocalDate today = tenantLocalToday(tenant);
        if (!force && today.equals(sub.getProjectedForDate())) {
            return sub;
        }
        applyAndPersist(tenant, sub, loadPeriods(List.of(sub.getId())), today);
        return sub;
    }

    @Override
    @SkipPermissionCheck
    public int refreshAll(Collection<TenantInfo> tenants) {
        List<TenantInfo> pending = tenants == null ? List.of() : tenants.stream()
                .filter(t -> t != null && t.getSubscriptionId() != null)
                .toList();
        if (pending.isEmpty()) {
            return 0;
        }
        // One batch read of the subscription rows, one of the period rows — never per tenant.
        Map<Long, TenantSubscription> subs = subscriptionService
                .searchList(new FlexQuery(Filters.of("id", Operator.IN,
                        pending.stream().map(TenantInfo::getSubscriptionId).distinct().toList())))
                .stream().collect(Collectors.toMap(TenantSubscription::getId, s -> s, (a, b) -> a));
        Map<Long, List<TenantSubscriptionPeriod>> periods = loadPeriods(subs.keySet());

        int rewritten = 0;
        for (TenantInfo tenant : pending) {
            TenantSubscription sub = subs.get(tenant.getSubscriptionId());
            if (sub == null) {
                log.warn("Subscription projection — tenant {} points at missing subscription {}",
                        tenant.getId(), tenant.getSubscriptionId());
                continue;
            }
            LocalDate today = tenantLocalToday(tenant);
            if (today.equals(sub.getProjectedForDate())) {
                continue;
            }
            if (applyAndPersist(tenant, sub, periods, today)) {
                rewritten++;
            }
        }
        return rewritten;
    }

    /**
     * Compute the projection into {@code sub}, persist it, and publish the entitlement-changed event when
     * an entitlement-relevant value moved.
     *
     * <p>Order matters: the row is written first, the event second. The event means "the projection has
     * changed", so publishing it first would let a consumer strip over-entitled role grants while the
     * projection still reads as the old plan.
     *
     * @return true when the row was rewritten
     */
    private boolean applyAndPersist(TenantInfo tenant, TenantSubscription sub,
                                    Map<Long, List<TenantSubscriptionPeriod>> periodsBySubscription,
                                    LocalDate today) {
        String previousPlanId = sub.getPlanId();
        List<TenantSubscriptionPeriod> periods =
                periodsBySubscription.getOrDefault(sub.getId(), List.of());

        // Overlaps are rejected on write, so normally at most one period covers a date. Should one slip in
        // anyway (a direct database write, an import, legacy rows), picking arbitrarily would make the
        // granted plan non-deterministic — and it could flip between refreshes, because authorization reads
        // this projection. So order the candidates and say so loudly: latest start wins, i.e. the most
        // recently sold period, with the id as a final tie-break.
        List<TenantSubscriptionPeriod> covering = periods.stream()
                .filter(p -> covers(p, today))
                .sorted(Comparator.comparing(TenantSubscriptionPeriod::getEffectiveStartDate).reversed()
                        .thenComparing(Comparator.comparing(TenantSubscriptionPeriod::getId).reversed()))
                .toList();
        if (covering.size() > 1) {
            log.error("Subscription projection — subscription {} has {} periods covering {}; this is "
                            + "corrupt data (overlaps are rejected on write). Using period {} (latest "
                            + "start). Overlapping ids: {}",
                    sub.getId(), covering.size(), today, covering.getFirst().getId(),
                    covering.stream().map(TenantSubscriptionPeriod::getId).toList());
        }
        TenantSubscriptionPeriod current = covering.isEmpty() ? null : covering.getFirst();
        TenantSubscriptionPeriod next = periods.stream()
                .filter(p -> p.getEffectiveStartDate() != null
                        && p.getEffectiveStartDate().isAfter(today))
                .min(Comparator.comparing(TenantSubscriptionPeriod::getEffectiveStartDate))
                .orElse(null);

        if (current != null) {
            sub.setCurrentPeriodId(current.getId());
            sub.setPlanId(current.getPlanId());
            sub.setPeriodType(current.getPeriodType());
            sub.setCurrentStartDate(current.getEffectiveStartDate());
            sub.setCurrentEndDate(current.getEffectiveEndDate());
            sub.setSubscriptionStatus(current.getPeriodType() == SubscriptionPeriodType.TRIAL
                    ? SubscriptionStatus.TRIAL
                    : SubscriptionStatus.PAID);
        } else {
            sub.setCurrentPeriodId(null);
            sub.setPlanId(null);
            sub.setPeriodType(null);
            sub.setCurrentStartDate(null);
            sub.setCurrentEndDate(null);
            // No covering period: distinguishing "scheduled" / "lapsed" / "never bought" costs nothing
            // extra here, and the three demand opposite follow-up from ops — see SubscriptionStatus.
            sub.setSubscriptionStatus(next != null
                    ? SubscriptionStatus.SCHEDULED
                    : periods.isEmpty()
                            ? SubscriptionStatus.NEVER_SUBSCRIBED
                            : SubscriptionStatus.EXPIRED);
        }
        sub.setNextStartDate(next == null ? null : next.getEffectiveStartDate());
        sub.setProjectedForDate(today);
        sub.setProjectedTime(LocalDateTime.now());

        // Every field above is assigned unconditionally: this is a full overwrite of the projected state, not
        // a patch. `updateProjection` is what makes that safe — it writes a named column map, so the nulls
        // land (a lapsed tenant must lose its plan) without the entity's virtual `periods` relation coming
        // along and being read as "clear the relation". See its javadoc; this cost a period table once.
        boolean written = subscriptionService.updateProjection(sub);

        // Only a plan change moves entitlement. Refreshing every tenant daily would otherwise turn the
        // downstream role cleanup into a daily full sweep.
        if (written && !Objects.equals(previousPlanId, sub.getPlanId())) {
            eventPublisher.publishEvent(new TenantEntitlementChangedEvent(tenant.getId()));
        }
        return written;
    }

    /** A period covers a date when it has started and has not ended; null end = open-ended. */
    private boolean covers(TenantSubscriptionPeriod period, LocalDate date) {
        if (period.getEffectiveStartDate() == null || period.getEffectiveStartDate().isAfter(date)) {
            return false;
        }
        return period.getEffectiveEndDate() == null || !period.getEffectiveEndDate().isBefore(date);
    }

    /** All periods of the given subscriptions, grouped. One query regardless of how many tenants. */
    private Map<Long, List<TenantSubscriptionPeriod>> loadPeriods(Collection<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return Map.of();
        }
        List<TenantSubscriptionPeriod> rows = modelService.searchList(PERIOD_MODEL,
                new FlexQuery(Filters.of("subscriptionId", Operator.IN, new ArrayList<>(subscriptionIds))),
                TenantSubscriptionPeriod.class);
        return rows.stream().collect(Collectors.groupingBy(TenantSubscriptionPeriod::getSubscriptionId));
    }
}
