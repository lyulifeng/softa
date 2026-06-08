package io.softa.starter.studio.release.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignAppVersionStatus;
import io.softa.starter.studio.release.enums.DesignAppVersionType;

/**
 * DesignAppVersion Model — represents a unit of change that can enter the release stream.
 * <p>
 * A Version contains the aggregated changelog data from its WorkItems, captured at seal time.
 * Deployment order is determined by version status, sealedTime, and the environment's
 * currentVersionId. DRAFT versions do not participate in deployment merge until sealed.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design App Version",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        defaultOrder = "id DESC"
)
public class DesignAppVersion extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Version Name", required = true, length = 64)
    private String name;

    @Field(label = "Version Type")
    private DesignAppVersionType versionType;

    @Field(label = "Upgrade description", length = 256)
    private String description;

    @Field(label = "Version Content")
    private JsonNode versionedContent;

    @Field(label = "Diff Hash", length = 64)
    private String diffHash;

    @Field(label = "Status")
    private DesignAppVersionStatus status;

    @Field(label = "Sealed Time")
    private LocalDateTime sealedTime;

    @Field(label = "Frozen Time")
    private LocalDateTime frozenTime;

    @Field(label = "Deleted")
    private Boolean deleted;
}
