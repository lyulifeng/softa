package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.tenant.enums.SubscriptionStatus;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;

/**
 * A tenant's subscription — one row per tenant, owned 1:1 by {@link TenantInfo} via
 * {@code TenantInfo.subscriptionId}. Kept off the TenantInfo columns on purpose: version management is
 * optional, so the core tenant registry stays billing-agnostic.
 *
 * <p><b>Every business column here is a projection</b> of this tenant's {@link TenantSubscriptionPeriod}
 * rows, computed as of the tenant's own local today. Ops never edits this row — it enters periods, and
 * {@code refreshProjection} is the only writer. Reading it is how both authorization and the UI answer
 * "what is this tenant on right now", which is why the columns are plain and filterable rather than
 * recomputed per request.
 *
 * <h3>Why a projection may be trusted for authorization</h3>
 * Because it carries {@link #projectedForDate} — the date it was computed for. Every reader compares that
 * against the tenant's local today first and recomputes on the spot when they differ, so a stale row
 * repairs itself on first touch instead of quietly handing out yesterday's plan. The scheduled refresh is
 * therefore a warm-up, not the guarantee. The previous design stored a bare lifecycle state with no such
 * marker: nothing could tell whether it was current, so correctness depended on a job having run, and
 * drifted whenever that assumption broke.
 *
 * <p>Compare with {@code !=}, never {@code <}: moving a tenant's timezone westward moves its local today
 * <i>backwards</i>, and a "projected before today" test would then never fire again.
 *
 * <p>All projected columns are nullable, {@link #projectedForDate} included. Null means "never
 * projected" — exactly what an existing row looks like right after this table gains the columns — and
 * since {@code null != today} always holds, the first read fills it in. That is why the migration needs
 * no backfill.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG,
        description = "A tenant's current subscription state, projected from its period rows")
public class TenantSubscription extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    /**
     * Which tenant this subscription belongs to.
     *
     * <p>Redundant with {@code TenantInfo.subscriptionId} — the framework's {@code ONE_TO_ONE} puts the FK on
     * the owner side, so that field is the link. This one exists because the link is unusable in the
     * opposite direction outside the app: given a subscription row, naming its customer means scanning
     * {@code tenant_info} for the row that points back. Every diagnostic query starts from the billing side.
     *
     * <p><b>Not a tenant partition key.</b> The name matches the convention everywhere else in the schema,
     * but this table is shared: it holds one row per tenant and is read across tenants by the platform
     * console and the projection cron. The framework only isolates by {@code tenant_id} when the model
     * declares {@code multiTenant}, which this one deliberately does not — isolation here comes from the
     * endpoints not being exposed to tenant admins. Turning {@code multiTenant} on would break the cron and
     * the console.
     *
     * <p>Written once, at provisioning. Nothing else touches it.
     */
    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = TenantInfo.class,
            description = "Owning tenant — a shortcut for queries starting from billing, not a partition key")
    private Long tenantId;

    /**
     * The Java name says {@code display} to keep anyone from authorizing on it — {@link #planId} is the only
     * entitlement input. But that warning is for readers of this class; to an operator "Display Status" reads
     * as the status of some display, so the label states what it actually answers.
     */
    @Field(label = "Subscription",
            description = "Projected: this tenant's subscription standing as of projectedForDate")
    private SubscriptionStatus subscriptionStatus;

    /**
     * Labelled "Plan" — the same label the period table uses, so one word means one thing across the two
     * places a plan appears. Not "Current Plan": the column it sits next to already says "Subscription",
     * and every projected column on this row is by definition as-of-today.
     */
    @Field(label = "Plan", fieldType = FieldType.MANY_TO_ONE, relatedModel = Plan.class,
            description = "Projected: plan of the period covering projectedForDate. Null = no covering "
                    + "period, i.e. the tenant runs on the floor plan")
    private String planId;

    @Field(description = "Projected: trial or paid, taken from the covering period")
    private SubscriptionPeriodType periodType;

    @Field(description = "Projected: id of the period row covering projectedForDate; null = none covers it")
    private Long currentPeriodId;

    @Field(renamedFrom = "effectiveFrom", description = "Projected: first day of the covering period")
    private LocalDate currentStartDate;

    @Field(renamedFrom = "effectiveTo",
            description = "Projected: last day of the covering period; null = open-ended. This is the "
                    + "expiry date to display")
    private LocalDate currentEndDate;

    @Field(description = "Projected: start date of the nearest upcoming period — drives the SCHEDULED "
            + "status and the 'starts on' line in the UI")
    private LocalDate nextStartDate;

    @Field(description = "Which date this projection was computed for, in the owning tenant's timezone. "
            + "Readers must compare it against that tenant's local today (with !=, not <) and recompute "
            + "when it differs. Null = never projected, which also triggers a recompute")
    private LocalDate projectedForDate;

    @Field(description = "When the projection was last refreshed — for diagnosing staleness")
    private LocalDateTime projectedTime;

    /**
     * The period rows this projection is computed from — a virtual field, no column.
     *
     * <p>It exists so the UI can render period inputs from metadata: a plan picker, an option dropdown and
     * real date calendars all come from {@code TenantSubscriptionPeriod}'s own field definitions, which a
     * form has no way to reach otherwise.
     *
     * <p><b>It is not a write path.</b> The framework's nested-relation pipeline would persist child rows
     * through the generic {@code ModelService}, which does not run
     * {@link io.softa.starter.tenant.service.TenantSubscriptionPeriodService}'s write guards or refresh this
     * projection afterwards. Periods are therefore only ever written by that service — the provisioning
     * request parses this relation itself and calls it per row, and both {@code /TenantSubscription/**} and
     * a nested {@code periods} patch on {@code /TenantInfo/updateOne} are rejected outright.
     */
    @Field(fieldType = FieldType.ONE_TO_MANY, relatedField = "subscriptionId",
            description = "Read + create-form input only; periods are written through their own guarded service")
    private List<TenantSubscriptionPeriod> periods;
}
