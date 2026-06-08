package io.softa.starter.studio.release.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * DesignAppEnvSnapshot Model — stores a full metadata snapshot for a DesignAppEnv.
 * <p>
 * Each successful deployment produces one snapshot row, uniquely keyed by
 * {@code (appId, envId, deploymentId)} (UNIQUE index enforced at the DB level).
 * The snapshot records the expected full state of runtime metadata at deployment time,
 * enabling drift detection between design-time and runtime, and per-deployment rollback.
 * <p>
 * The "current" snapshot for an env is the one belonging to the latest deployment
 * (ordered by id / deploymentId DESC).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design App Env Snapshot",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
@Index(indexName = "unique_env_snapshot", fields = {"appId", "envId", "deploymentId"}, unique = true)
public class DesignAppEnvSnapshot extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID", required = true)
    private Long appId;

    @Field(label = "Env ID", required = true)
    private Long envId;

    @Field(label = "Deployment ID", required = true)
    private Long deploymentId;

    @Field(label = "Snapshot")
    private JsonNode snapshot;

    @Field(label = "Deleted")
    private Boolean deleted;
}
