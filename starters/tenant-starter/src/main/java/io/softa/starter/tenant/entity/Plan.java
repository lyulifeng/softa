package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * Plan catalog — the sellable plan tiers. System-level seed data, shared by all tenants, authored per
 * deployment (ids / tiers / module sets are the app's, not the framework's). Code-as-id: {@link #id}
 * IS the plan code. The <b>lowest-tier</b> plan is the entitlement fallback/floor (see
 * {@code EntitlementResolver}) — no plan id is hardcoded anywhere.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.EXTERNAL_ID, businessKey = {"id"}, defaultOrder = {"tier:asc"},
        description = "Plan catalog — sellable plan tiers (deployment-authored)")
public class Plan extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID", length = 64, description = "Plan code (code-as-id; the plan's business id)")
    private String id;

    @Field(required = true, length = 64, description = "Display name")
    private String name;

    @Field(description = "Ordering + up/down-grade direction (e.g. 0/10/20) — NOT an auth predicate; "
            + "the lowest tier is the entitlement fallback/floor")
    private Integer tier;

    @Field(description = "Whether the plan is currently sellable")
    private Boolean active;

    @Field(length = 256, description = "Description")
    private String description;

    /** The modules this plan entitles — the plan_entitlement child rows. Virtual relation (no
     *  column): lets the seed nest entitlements under a Plan and callers read a plan with its
     *  module set in one query. The child FK is {@code PlanEntitlement.planId}. */
    @Field(fieldType = FieldType.ONE_TO_MANY, relatedModel = PlanEntitlement.class, relatedField = "planId",
            description = "Modules this plan entitles (child plan_entitlement rows)")
    private List<PlanEntitlement> entitlements;
}
