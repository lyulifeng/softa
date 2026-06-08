package io.softa.starter.studio.release.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignDeploymentDdlStatus;
import io.softa.starter.studio.release.enums.DesignDeploymentStatus;

/**
 * DesignDeployment Model — the immutable deployment record produced when a Version is deployed to an Env.
 * <p>
 * A Deployment is the single deployment artifact. It is created during the deployment process:
 * the system selects released versions in sealedTime order from the Env's
 * {@code currentVersionId} (sourceVersionId) to the target Version (targetVersionId),
 * merges their version contents via
 * {@link io.softa.starter.studio.release.version.VersionMerger}, generates DDL, and stores everything
 * on this entity along with execution results.
 * <p>
 * Lifecycle: {@code PENDING} → {@code DEPLOYING} → {@code SUCCESS} / {@code FAILURE} / {@code ROLLED_BACK}
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Deployment",
        defaultOrder = "id DESC",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class DesignDeployment extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID", required = true)
    private Long appId;

    @Field(label = "Env ID", required = true, description = "The environment this deployment targets")
    private Long envId;

    @Field(label = "Source Version", required = true,
            description = "The Env's currentVersionId at deployment time (null for first deploy)")
    private Long sourceVersionId;

    @Field(label = "Target Version", required = true, description = "The version being deployed")
    private Long targetVersionId;

    @Field(label = "Name", length = 64)
    private String name;

    @Field(label = "Deploy Status")
    private DesignDeploymentStatus deployStatus;

    @Field(label = "Deploy Duration (S)")
    private Double deployDuration;

    @Field(label = "Deployment Summary", length = 20000)
    private String summary;

    @Field(label = "Diff Hash", length = 64, description = "SHA-256 of the serialized mergedContent")
    private String diffHash;

    @Field(label = "Merged Content",
            description = "The merged versionedContent from the sealedTime release interval")
    private JsonNode mergedContent;

    @Field(label = "Merged Table DDL", length = 20000, description = "Combined DDL for table structure changes")
    private String mergedDdlTable;

    @Field(label = "Merged Index DDL", length = 20000, description = "Combined DDL for index changes")
    private String mergedDdlIndex;

    @Field(label = "DDL Apply Status",
            description = "Rollup of per-statement outcomes "
                    + "(NOT_REQUIRED / PENDING_RUNTIME / AUTO_APPLIED / AUTO_APPLY_PARTIAL_FAILED / "
                    + "PENDING_DBA_APPROVAL / DBA_RESOLVING / DBA_APPLIED / DBA_REJECTED). "
                    + "Recomputed on every change to ddlApplyResults — see ADR-0009 #6")
    private DesignDeploymentDdlStatus ddlApplyStatus;

    @Field(label = "DDL Apply Results",
            description = "JSON array of per-statement records "
                    + "{ sequence, sql, status, errorMessage, actedBy, actedAt }, sequence-aligned "
                    + "with the deploy envelope's ddlStatements (ADR-0009 #6)")
    private JsonNode ddlApplyResults;

    @Field(label = "Version List", fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignDeploymentVersion.class, relatedField = "deploymentId")
    private List<DesignDeploymentVersion> versions;

    @Field(label = "Started Time")
    private LocalDateTime startedTime;

    @Field(label = "Finished Time")
    private LocalDateTime finishedTime;

    @Field(label = "Callback Token", length = 128,
            description = "One-time token sent to the runtime and verified on webhook return")
    private String callbackToken;

    @Field(label = "Callback Token Expire At", description = "Tokens received after this point are rejected")
    private LocalDateTime callbackTokenExpireAt;

    @Field(label = "Callback Received At", description = "When the runtime webhook landed")
    private LocalDateTime callbackReceivedAt;

    @Field(label = "Operator")
    private Long operatorId;

    @Field(label = "Error Message", length = 20000)
    private String errorMessage;

    @Field(label = "Deleted")
    private Boolean deleted;
}
