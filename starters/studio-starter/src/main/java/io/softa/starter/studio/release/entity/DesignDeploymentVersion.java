package io.softa.starter.studio.release.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;

/**
 * DesignDeploymentVersion Model — audit record linking a Deployment to the Versions it merged.
 * <p>
 * These records are auto-generated when a Deployment is created from the sealedTime release interval.
 * The sequence reflects the order in which versions were applied during the merge
 * (ascending by sealedTime up to the target version).
 */
@Data
@Model(idStrategy = IdStrategy.DISTRIBUTED_LONG, copyable = false)
@EqualsAndHashCode(callSuper = true)
public class DesignDeploymentVersion extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Deployment ID")
    private Long deploymentId;

    @Field(label = "Version ID")
    private Long versionId;

    @Field(description = "Merge sequence — ascending order in which versions were applied")
    private Integer sequence;
}
