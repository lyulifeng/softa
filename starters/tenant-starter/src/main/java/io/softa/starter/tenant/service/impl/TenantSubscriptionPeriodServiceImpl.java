package io.softa.starter.tenant.service.impl;

import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Objects;

import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.Plan;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.SubscriptionPeriodPatch;
import io.softa.starter.tenant.service.SubscriptionProjectionService;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;

/**
 * {@link TenantSubscriptionPeriodService} — the guards and the projection refresh live here, so this is
 * the only sanctioned way into the table.
 *
 * <p>Every mutating override follows the same shape: validate, delegate to the framework, then refresh the
 * owning subscription's projection. The refresh is deliberately <b>not</b> deferred to the hourly job:
 * ops expects the tenant list to reflect what it just entered.
 */
@Slf4j
@Service
public class TenantSubscriptionPeriodServiceImpl
        extends EntityServiceImpl<TenantSubscriptionPeriod, Long>
        implements TenantSubscriptionPeriodService {

    @Autowired
    private ModelService<?> modelService;

    @Autowired
    private SubscriptionProjectionService projectionService;

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long createOne(TenantSubscriptionPeriod entity) {
        stampOwnerTenant(entity);
        validate(entity, null);
        Long id = super.createOne(entity);
        refreshOwner(entity.getSubscriptionId());
        return id;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean updateOne(TenantSubscriptionPeriod entity) {
        Assert.notNull(entity == null ? null : entity.getId(), "Period id is required to update it.");
        // Also on update: it repairs a row whose tenant was never stamped (anything predating the column).
        stampOwnerTenant(entity);
        TenantSubscriptionPeriod merged = merge(entity);
        validate(merged, merged.getId());
        boolean ok = super.updateOne(entity);
        refreshOwner(merged.getSubscriptionId());
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteById(Long id) {
        Long owner = ownerOf(id);
        boolean ok = super.deleteById(id);
        refreshOwner(owner);
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public boolean deleteByIds(List<Long> ids) {
        List<Long> owners = ids == null ? List.of() : ids.stream().map(this::ownerOf)
                .filter(Objects::nonNull).distinct().toList();
        boolean ok = super.deleteByIds(ids);
        owners.forEach(this::refreshOwner);
        return ok;
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public Long changePlanNow(Long subscriptionId, String planId, SubscriptionPeriodType periodType) {
        Assert.notNull(subscriptionId, "Subscription id is required.");
        LocalDate today = projectionService.tenantLocalToday(ownerTenant(subscriptionId));
        TenantSubscriptionPeriod current = periodsOf(subscriptionId).stream()
                .filter(p -> covers(p, today))
                .findFirst()
                .orElse(null);
        Assert.notNull(current, "This tenant has no period in effect today; record a new period instead.");

        // Inherit the paid-through date before closing the old period off, otherwise the customer loses
        // the remainder of what they already paid for.
        LocalDate inheritedEnd = current.getEffectiveEndDate();
        if (today.equals(current.getEffectiveStartDate())) {
            // Changed on the same day it started — correct that period in place rather than leaving a
            // zero-length one behind.
            current.setPlanId(planId);
            current.setPeriodType(periodType);
            updateOne(current);
            return current.getId();
        }
        current.setEffectiveEndDate(today.minusDays(1));
        updateOne(current);

        TenantSubscriptionPeriod next = new TenantSubscriptionPeriod();
        next.setSubscriptionId(subscriptionId);
        next.setPlanId(planId);
        next.setPeriodType(periodType);
        next.setEffectiveStartDate(today);
        next.setEffectiveEndDate(inheritedEnd);
        return createOne(next);
    }

    @Override
    @Transactional(rollbackFor = Exception.class)
    public void applyPatch(Long subscriptionId, SubscriptionPeriodPatch patch) {
        Assert.notNull(subscriptionId, "Subscription id is required to edit its periods.");
        if (patch == null) {
            return;
        }
        // Logged because a patch that binds partially is otherwise invisible: the request succeeds, the
        // projection is refreshed against whatever did land, and the row the user filled in is simply not
        // there. The plan is included because it is the field most likely to arrive empty — the UI sends a
        // reference, and only its id belongs in this DTO.
        log.info("Subscription {} period patch — create={} update={} delete={} plans={}", subscriptionId,
                nonNull(patch.getCreate()).size(), nonNull(patch.getUpdate()).size(),
                nonNull(patch.getDelete()).size(),
                nonNull(patch.getCreate()).stream().map(SubscriptionPeriodPatch.PeriodInput::getPlanId).toList());

        // Delete first so an interval being replaced is free before anything claims it; create last so a
        // new row is checked against the state the rest of the patch already produced.
        List<Long> toDelete = nonNull(patch.getDelete());
        if (!toDelete.isEmpty()) {
            deleteByIds(toDelete);
        }
        for (SubscriptionPeriodPatch.PeriodInput row : nonNull(patch.getUpdate())) {
            Assert.notNull(row.getId(), "Period id is required to update it.");
            updateOne(toEntity(row, subscriptionId));
        }
        for (SubscriptionPeriodPatch.PeriodInput row : nonNull(patch.getCreate())) {
            // Deliberately NOT skipped when the plan is blank. That is what an earlier version did, on the
            // theory that a blank row was one the form had left behind — and it silently swallowed rows the
            // user had filled in, because a plan that fails to bind also arrives blank. `validate` already
            // requires a plan, so letting the row through turns invisible data loss into a message.
            TenantSubscriptionPeriod period = toEntity(row, subscriptionId);
            period.setId(null);
            if (period.getPeriodType() == null) {
                period.setPeriodType(SubscriptionPeriodType.PAID);
            }
            if (period.getEffectiveStartDate() == null) {
                period.setEffectiveStartDate(projectionService.tenantLocalToday(ownerTenant(subscriptionId)));
            }
            createOne(period);
        }
        // Refresh once more at the end, against the subscription the caller named.
        //
        // Every operation above refreshes on its own, but each derives the owner from the row it touched —
        // and `deleteByIds` derives it by loading the row, so an id that no longer exists yields no owner
        // and the refresh is silently skipped. A patch is then able to change which periods exist while
        // leaving the projection describing the old ones, which is the one state this table must never be
        // in: authorization reads the projection, and `currentPeriodId` would point at a row that is gone.
        // Here the owner is an argument, so it cannot go missing.
        refreshOwner(subscriptionId);
    }

    /**
     * Derive the period's tenant from its subscription.
     *
     * <p>Always overwritten rather than filled in when absent: a caller-supplied value has no authority
     * here — the subscription decides whose period this is, and accepting anything else would let a request
     * label one tenant's period as another's.
     */
    private void stampOwnerTenant(TenantSubscriptionPeriod entity) {
        if (entity == null || entity.getSubscriptionId() == null) {
            return;
        }
        TenantInfo tenant = ownerTenant(entity.getSubscriptionId());
        entity.setTenantId(tenant == null ? null : tenant.getId());
    }

    private static <T> List<T> nonNull(List<T> list) {
        return list == null ? List.of() : list.stream().filter(Objects::nonNull).toList();
    }

    /**
     * The owning subscription comes from the tenant, never from the payload — otherwise a crafted request
     * could attach a period to someone else's subscription.
     */
    private TenantSubscriptionPeriod toEntity(SubscriptionPeriodPatch.PeriodInput row, Long subscriptionId) {
        TenantSubscriptionPeriod period = new TenantSubscriptionPeriod();
        period.setId(row.getId());
        period.setSubscriptionId(subscriptionId);
        period.setPlanId(row.getPlanId());
        period.setPeriodType(row.getPeriodType());
        period.setEffectiveStartDate(row.getEffectiveStartDate());
        period.setEffectiveEndDate(row.getEffectiveEndDate());
        return period;
    }

    /**
     * The four guards. {@code selfId} is the row being updated, excluded from the overlap scan so a row
     * never conflicts with itself.
     */
    private void validate(TenantSubscriptionPeriod period, Long selfId) {
        Assert.notNull(period, "Period is required.");
        Assert.notNull(period.getSubscriptionId(), "Period must belong to a subscription.");
        Assert.notNull(period.getEffectiveStartDate(), "Period start date is required.");
        Assert.notBlank(period.getPlanId(), "Period plan is required.");
        Assert.notNull(period.getPeriodType(), "Period type (trial / paid) is required.");

        if (period.getEffectiveEndDate() != null) {
            Assert.isTrue(!period.getEffectiveEndDate().isBefore(period.getEffectiveStartDate()),
                    "Period end date {0} cannot precede its start date {1}.",
                    period.getEffectiveEndDate(), period.getEffectiveStartDate());
        }

        Plan floor = floorPlan();
        if (floor != null) {
            // A floor period and no period express the same state; allowing both would give one state two
            // representations, and the projection could not tell them apart.
            Assert.notEqual(period.getPlanId(), floor.getId(),
                    "The floor plan cannot be sold as a period — every tenant already has it.");
            if (period.getPeriodType() == SubscriptionPeriodType.TRIAL) {
                Plan plan = planById(period.getPlanId());
                int tier = plan != null && plan.getTier() != null ? plan.getTier() : 0;
                int floorTier = floor.getTier() != null ? floor.getTier() : 0;
                Assert.isTrue(tier > floorTier,
                        "Only plans above the floor can be trialled.");
            }
        }

        // No database constraint can express "no two periods of one subscription overlap", so it is
        // enforced here — on updates as well, because widening an ended period's dates over a gap does
        // the same damage as inserting an overlapping row.
        for (TenantSubscriptionPeriod other : periodsOf(period.getSubscriptionId())) {
            if (selfId != null && selfId.equals(other.getId())) {
                continue;
            }
            Assert.notTrue(overlaps(period, other),
                    "This period overlaps an existing one ({0} to {1}).",
                    other.getEffectiveStartDate(),
                    other.getEffectiveEndDate() == null ? "open-ended" : other.getEffectiveEndDate());
        }
    }

    /** Two periods overlap when neither ends strictly before the other starts. Null end = open-ended. */
    private boolean overlaps(TenantSubscriptionPeriod a, TenantSubscriptionPeriod b) {
        boolean aEndsBeforeB = a.getEffectiveEndDate() != null
                && a.getEffectiveEndDate().isBefore(b.getEffectiveStartDate());
        boolean bEndsBeforeA = b.getEffectiveEndDate() != null
                && b.getEffectiveEndDate().isBefore(a.getEffectiveStartDate());
        return !aEndsBeforeB && !bEndsBeforeA;
    }

    private boolean covers(TenantSubscriptionPeriod period, LocalDate date) {
        if (period.getEffectiveStartDate() == null || period.getEffectiveStartDate().isAfter(date)) {
            return false;
        }
        return period.getEffectiveEndDate() == null || !period.getEffectiveEndDate().isBefore(date);
    }

    /**
     * An update payload may carry only the changed fields, so the guards need the stored row underneath —
     * validating the patch alone would let a half-specified update slip past the overlap check.
     */
    private TenantSubscriptionPeriod merge(TenantSubscriptionPeriod patch) {
        TenantSubscriptionPeriod stored = getById(patch.getId())
                .orElseThrow(() -> new IllegalArgumentException("Period " + patch.getId() + " not found."));
        if (patch.getSubscriptionId() != null) {
            stored.setSubscriptionId(patch.getSubscriptionId());
        }
        if (patch.getEffectiveStartDate() != null) {
            stored.setEffectiveStartDate(patch.getEffectiveStartDate());
        }
        if (patch.getEffectiveEndDate() != null) {
            stored.setEffectiveEndDate(patch.getEffectiveEndDate());
        }
        if (patch.getPlanId() != null) {
            stored.setPlanId(patch.getPlanId());
        }
        if (patch.getPeriodType() != null) {
            stored.setPeriodType(patch.getPeriodType());
        }
        return stored;
    }

    /** Package-private so a test can observe that every write path ends up here. */
    void refreshOwner(Long subscriptionId) {
        TenantInfo tenant = ownerTenant(subscriptionId);
        if (tenant == null) {
            log.warn("Subscription {} has no owning tenant — projection not refreshed", subscriptionId);
            return;
        }
        // Unconditional: the projection is still marked current for today, but the periods under it just
        // moved, so the staleness gate would wrongly decline.
        projectionService.refreshNow(tenant);
    }

    private TenantInfo ownerTenant(Long subscriptionId) {
        if (subscriptionId == null) {
            return null;
        }
        List<TenantInfo> tenants = modelService.searchList("TenantInfo",
                new FlexQuery(Filters.of("subscriptionId", Operator.EQUAL, subscriptionId)),
                TenantInfo.class);
        return tenants.isEmpty() ? null : tenants.getFirst();
    }

    private Long ownerOf(Long periodId) {
        return periodId == null ? null
                : getById(periodId).map(TenantSubscriptionPeriod::getSubscriptionId).orElse(null);
    }

    private List<TenantSubscriptionPeriod> periodsOf(Long subscriptionId) {
        return searchList(new FlexQuery(
                Filters.of("subscriptionId", Operator.EQUAL, subscriptionId)));
    }

    private Plan floorPlan() {
        List<Plan> plans = modelService.searchList("Plan", new FlexQuery(new Filters()), Plan.class);
        return plans.stream()
                .filter(p -> p.getTier() != null)
                .min(Comparator.comparingInt(Plan::getTier).thenComparing(Plan::getId))
                .orElse(null);
    }

    private Plan planById(String planId) {
        List<Plan> plans = modelService.searchList("Plan",
                new FlexQuery(Filters.of("id", Operator.EQUAL, planId)), Plan.class);
        return plans.isEmpty() ? null : plans.getFirst();
    }
}
