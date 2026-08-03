package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.time.LocalDate;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.OnDelete;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;

/**
 * One row = one subscription period: "this tenant is on this plan from this date to that date".
 *
 * <p><b>This is the record layer</b> — what ops actually enters. A tenant may hold several periods at
 * once (the current one plus renewals already agreed for later), and gaps between them are legitimate:
 * a gap simply means the tenant runs on the floor plan then.
 *
 * <p>The floor plan is never recorded here. Every tenant has it by birth, so a "floor period" and "no
 * period" would say the same thing two ways — {@link #planId} rejects the floor plan for that reason.
 *
 * <p>No status column. Where a period sits relative to a date is a pure function of
 * {@code (effectiveStartDate, effectiveEndDate, thatDate)}; storing it would create a second version of
 * the truth that has to be chased by a job. The owning {@link TenantSubscription} row does carry a
 * projected status, but it carries a projection date with it so readers can tell whether it is stale.
 *
 * <p><b>Overlap is not enforceable in the database</b> — no column constraint can express "no two
 * periods of the same subscription may overlap", so the service layer guards it, on inserts
 * <i>and</i> on updates. Editing an already-ended period's dates is exactly as damaging as inserting an
 * overlapping one, so both go through the same single write entry point.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG,
        description = "One subscription period of a tenant — the record layer behind TenantSubscription")
// "This customer's billing history" is the query this table exists to answer, and the tenant is how anyone
// asks it — every other access already goes through the unique key below.
@Index(fields = {"tenantId"})
@Index(indexName = "uk_tenant_subscription_period", fields = {"subscriptionId", "effectiveStartDate"},
        unique = true,
        message = "This subscription already has a period starting on that date.")
public class TenantSubscriptionPeriod extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = TenantSubscription.class, required = true,
            onDelete = OnDelete.CASCADE,
            description = "Owning subscription row; deleting it deletes all of this tenant's periods")
    private Long subscriptionId;

    /**
     * Which tenant this period was sold to.
     *
     * <p>Denormalized: it follows from {@link #subscriptionId}, but only through two hops and one of them
     * runs backwards ({@code tenant_info.subscription_id} points here, not the other way). Reading a row in a
     * database client and asking "whose period is this" is the single most common thing anyone does with this
     * table, and without this column it takes two lookups.
     *
     * <p>Kept honest by having exactly one writer: {@code createOne} / {@code updateOne} derive it from the
     * owning subscription, so it cannot be supplied by a caller and cannot drift from the FK. It is
     * deliberately absent from the request DTO for the same reason.
     *
     * <p><b>Not a tenant partition key</b> — see the note on {@code TenantSubscription.tenantId}. This table
     * holds every customer's billing history in one place on purpose.
     */
    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = TenantInfo.class,
            description = "Tenant this period was sold to — derived from the subscription, never supplied")
    private Long tenantId;

    @Field(required = true, description = "First day this period is in effect (inclusive)")
    private LocalDate effectiveStartDate;

    @Field(description = "Last day this period is in effect (inclusive); null = open-ended")
    private LocalDate effectiveEndDate;

    /** "Plan", not "Plan Id": this is a column ops edits, and the picker shows the plan's name anyway. */
    @Field(label = "Plan", fieldType = FieldType.MANY_TO_ONE, relatedModel = Plan.class, required = true,
            description = "Plan sold for this period. Must NOT be the floor plan — a floor period and "
                    + "no period express the same thing, so the service layer rejects it")
    private String planId;

    @Field(required = true,
            description = "Trial or paid. Does NOT affect entitlement — it only drives reminder wording, "
                    + "the projected display status, and the guard that trials must be above the floor")
    private SubscriptionPeriodType periodType;

    @Field(description = "Tenant-local date an expiry reminder was last sent for this period — the "
            + "idempotency guard so at most one reminder goes out per tenant-local day")
    private LocalDate lastReminderDate;
}
