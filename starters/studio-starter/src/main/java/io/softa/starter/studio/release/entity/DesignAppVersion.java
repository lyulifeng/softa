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
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        defaultOrder = "id DESC",
        copyable = false
)
public class DesignAppVersion extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Version Name", required = true)
    private String name;

    @Field
    private DesignAppVersionType versionType;

    @Field(label = "Upgrade description", length = 256)
    private String description;

    @Field(label = "Version Content")
    private JsonNode versionedContent;

    @Field
    private String diffHash;

    @Field
    private DesignAppVersionStatus status;

    @Field(copyable = false)
    private LocalDateTime sealedTime;

    @Field(copyable = false)
    private LocalDateTime frozenTime;

    @Field
    private Boolean deleted;
}
