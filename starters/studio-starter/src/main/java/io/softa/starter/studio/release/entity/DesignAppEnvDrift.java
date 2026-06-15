package io.softa.starter.studio.release.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignDriftCheckStatus;

/**
 * DesignAppEnvDrift — cached result of the last drift check for a {@code DesignAppEnv}.
 * <p>
 * One row per env (unique index on {@code env_id}). The drift comparison is an
 * expensive operation (parallel RPC to the runtime, per-row diffing); it is performed
 * automatically once after every successful deployment and on manual operator request,
 * then cached here so the public {@code compareDesignWithRuntime(envId)} endpoint
 * serves results in O(1) DB reads.
 * <p>
 * {@code driftContent} is a JSON list of {@code ModelChangesDTO} — empty when there is
 * no drift, populated otherwise. {@code hasDrift} mirrors that flag for cheap filtering
 * without deserializing the JSON.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        copyable = false
)
@Index(indexName = "unique_env_drift", fields = {"appId", "envId"}, unique = true)
public class DesignAppEnvDrift extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID", required = true)
    private Long appId;

    @Field(label = "Env ID", required = true)
    private Long envId;

    @Field(required = true)
    private Boolean hasDrift;

    @Field
    private JsonNode driftContent;

    @Field(required = true)
    private DesignDriftCheckStatus checkStatus;

    @Field(length = 1024)
    private String errorMessage;

    @Field
    private LocalDateTime lastCheckedTime;

    @Field
    private Boolean deleted;
}
