package io.softa.starter.tenant.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;

/**
 * Typed CRUD for subscription periods — <b>the single write entry point</b> for the record layer.
 *
 * <p>Two things happen on every write here and nowhere else: the four guards (§ below) and the projection
 * refresh of the owning subscription row. Anything that reaches the table without passing through this
 * interface skips both, which is why the generic model endpoints for this model are shadowed rather than
 * left open — see {@code TenantSubscriptionPeriodController}.
 *
 * <h3>Guards</h3>
 * <ol>
 *   <li><b>{@code planId} must not be the floor plan.</b> A floor period and no period say the same
 *       thing; allowing both would give the same state two representations.</li>
 *   <li><b>{@code TRIAL} only above the floor.</b> Trialling the free tier is meaningless.</li>
 *   <li><b>No two periods of one subscription may overlap.</b> An overlap makes "the period covering
 *       today" ambiguous, and the projection would then pick one arbitrarily — a wrong plan, not a wrong
 *       label. No database constraint can express this, so it is checked here.</li>
 *   <li><b>End date must not precede start date.</b></li>
 * </ol>
 *
 * <p>Guard 3 applies to updates as much as inserts. Moving an already-ended period's start date backwards
 * — or its end date forwards over a gap — damages exactly as much as inserting an overlapping row, so
 * both paths are validated identically. Back-filling a <i>non-overlapping</i> historical period stays
 * allowed: a purely past interval cannot change which period covers today, and ops needs it to record
 * contracts entered late.
 */
public interface TenantSubscriptionPeriodService extends EntityService<TenantSubscriptionPeriod, Long> {

    /**
     * Move a tenant to another plan effective today, keeping the paid-through date.
     *
     * <p>Two writes in one transaction: the covering period is closed off the day before, and a new period
     * inherits its original end date. Split across transactions there would be a moment with a gap in the
     * record, and an authorization landing in it would resolve to the floor plan.
     *
     * @param subscriptionId owning subscription
     * @param planId         plan to move to (must not be the floor plan)
     * @param periodType     trial or paid for the new period
     * @return the newly created period's id
     */
    Long changePlanNow(Long subscriptionId, String planId,
                       io.softa.starter.tenant.enums.SubscriptionPeriodType periodType);

    /**
     * Replay a UI relation patch through this service, one operation at a time.
     *
     * <p>Exists so the periods of a tenant can be edited as an inline relation table — the same table on the
     * create form and on the detail form — without the framework's nested-relation pipeline persisting them.
     * That pipeline goes through the generic {@code ModelService}, which runs none of the guards below and
     * leaves the projection stale; replaying the operations here keeps one form shape and one write path.
     *
     * <p>Order is <b>delete, then update, then create</b>, and it matters: freeing an interval before
     * anything claims it lets ops replace a period in a single submit. Creating first would have the new row
     * collide with the very row being deleted, and the overlap guard would reject a legitimate edit.
     *
     * <p>One transaction: a patch that fails half-way would otherwise leave the record with a gap, and an
     * authorization landing in it would resolve to the floor plan.
     *
     * @param subscriptionId owning subscription — taken from the tenant, never from the payload
     * @param patch          operations to apply; null / empty is a no-op
     */
    void applyPatch(Long subscriptionId, SubscriptionPeriodPatch patch);
}
