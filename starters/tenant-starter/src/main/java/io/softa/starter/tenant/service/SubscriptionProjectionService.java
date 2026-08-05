package io.softa.starter.tenant.service;

import java.time.LocalDate;
import java.util.Collection;

import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;

/**
 * Keeps each tenant's {@link TenantSubscription} row in step with its
 * {@link io.softa.starter.tenant.entity.TenantSubscriptionPeriod} rows. <b>The only writer of the
 * projected columns.</b>
 *
 * <p>Three callers, one method: the period write path calls it after commit so ops sees the effect
 * immediately; the hourly job calls the batch variant so most tenants are already current; and any reader
 * that finds a stale {@code projectedForDate} calls it inline. The last one is what makes the projection
 * safe to authorize against — the job is a warm-up, not the guarantee, and a job outage degrades to
 * "recomputed on first touch" rather than "wrong plan served".
 *
 * <p>Staleness is {@code projectedForDate != tenantLocalToday}. Deliberately not {@code <}: a tenant
 * moved westward (say {@code UTC+13} to {@code UTC-11}) has its local today move <i>backwards</i>, and a
 * "projected before today" test would then never fire again, freezing the projection in the future.
 */
public interface SubscriptionProjectionService {

    /**
     * Recompute one tenant's projection as of its local today and persist it. No-op when already current,
     * so it is safe to call on every read.
     *
     * <p>Publishes {@code TenantEntitlementChangedEvent} <b>only when a projected value actually
     * changed</b> — refreshing every tenant daily would otherwise turn the role-cleanup chain into a
     * daily full sweep. The event is published after the row is written, never before: its meaning is
     * "the projection has changed", and reversing the order would let a consumer strip over-entitled
     * role grants while the projection still reads as the old plan.
     *
     * @param tenant owning tenant — supplies both the subscription id and the timezone
     * @return the current projection; the unchanged row when it was already fresh
     */
    TenantSubscription refresh(TenantInfo tenant);

    /**
     * Recompute unconditionally, skipping the staleness gate.
     *
     * <p>For the period write path: right after a period changes the projection is still marked current
     * for today, so the gated variant would decline to run — yet the periods underneath it just moved.
     *
     * @param tenant owning tenant
     * @return the recomputed projection
     */
    TenantSubscription refreshNow(TenantInfo tenant);

    /**
     * Batch variant for the scheduled sweep. Must resolve every tenant's periods with a bounded number of
     * queries — never one per row.
     *
     * <p>This is affordable because the timezone span is 26 hours ({@code UTC-12} to {@code UTC+14}), so
     * at any instant there are at most three distinct "local today" values worldwide: take the earliest
     * one as the query floor, fetch each subscription's covering and next period in one grouped pass,
     * then decide per tenant against its own local today.
     *
     * @param tenants tenants to sweep
     * @return how many projections were actually rewritten
     */
    int refreshAll(Collection<TenantInfo> tenants);

    /**
     * Today in the tenant's own timezone — the reference date for every as-of decision in this domain:
     * which period is current, when a period expires, when a reminder is due, and what a projection is
     * valid for. Falls back to UTC when the tenant has no timezone set.
     */
    LocalDate tenantLocalToday(TenantInfo tenant);
}
