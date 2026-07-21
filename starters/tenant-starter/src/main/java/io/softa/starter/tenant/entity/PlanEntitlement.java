package io.softa.starter.tenant.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * Plan → module grant (plan-tier definition). System-level seed data (~15 rows). No
 * physical FK to {@code plan} (no-FK-ref philosophy) — validated at boot by the plan
 * entitlement check. Unique per {@code (planId, moduleId)}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG,
        description = "Plan → module grant (plan-tier definition)")
@Index(indexName = "uk_plan_entitlement_plan_module", fields = {"planId", "moduleId"}, unique = true)
public class PlanEntitlement extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true, length = 64, description = "Plan code (= plan.id); no physical FK")
    private String planId;

    @Field(required = true, length = 32, description = "Module id (nav id first segment): core-hr / attendance / ai / …")
    private String moduleId;
}
