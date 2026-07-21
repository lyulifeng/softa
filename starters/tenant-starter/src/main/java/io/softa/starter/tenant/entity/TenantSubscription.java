package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.tenant.enums.TenantLifecycle;

/**
 * Tenant version / subscription — the version axis, in its own table owned 1:1 by
 * {@link TenantInfo} via {@code TenantInfo.subscriptionId} (the owner-side FK). Kept off the
 * TenantInfo columns on purpose: version management is optional, so the core tenant registry
 * stays billing-agnostic — apps that don't sell versions simply leave {@code subscriptionId}
 * unset and never create a row here.
 *
 * <p>Two version fields drive entitlement: {@link #planId} (which plan) + {@link #lifecycle}
 * (TRIAL / SUBSCRIBED / GRACE_PERIOD active; EXPIRED degrades to Free). The resolver gates on
 * {@code lifecycle} only — it does NOT read {@link #effectiveFrom} / {@link #effectiveTo} directly.
 * Instead {@code SubscriptionExpiryJob} flips {@code lifecycle} to EXPIRED once {@code effectiveTo}
 * has passed in the owning tenant's own timezone — i.e. at that tenant's local midnight (Ops may also
 * flip it manually), so the dates drive expiry via the job while {@code lifecycle} stays the single
 * source of truth the resolver reads.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG,
        description = "Tenant version / subscription — owned 1:1 by TenantInfo.subscriptionId")
public class TenantSubscription extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = Plan.class, required = true,
            description = "Plan (FK to plan.id, code-as-id) for this tenant")
    private String planId;

    @Field(description = "Subscription lifecycle: TRIAL / SUBSCRIBED / GRACE_PERIOD (active) / EXPIRED (→ Free)")
    private TenantLifecycle lifecycle;

    @Field(description = "Version effective start date (informational / audit)")
    private LocalDate effectiveFrom;

    @Field(description = "Version end / expiry date (null = open-ended); the subscription lapses to Free at "
            + "the owning tenant's local midnight after this date — SubscriptionExpiryJob flips lifecycle → EXPIRED")
    private LocalDate effectiveTo;

    @Field(description = "Tenant-local date an expiry reminder was last sent for this subscription — the "
            + "idempotency guard so SubscriptionExpiryJob sends at most one reminder per tenant-local day")
    private LocalDate lastReminderDate;
}
