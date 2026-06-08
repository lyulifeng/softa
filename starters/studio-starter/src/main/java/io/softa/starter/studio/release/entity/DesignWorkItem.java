package io.softa.starter.studio.release.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignWorkItemStatus;

/**
 * DesignWorkItem Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Work Item",
        idStrategy = IdStrategy.DISTRIBUTED_LONG
)
public class DesignWorkItem extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID", required = true)
    private Long appId;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Status")
    private DesignWorkItemStatus status;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Closed Time", description = "Closed Time — when the WorkItem was released to prod.")
    private LocalDateTime closedTime;

    @Field(label = "Version ID", description = "Version ID — the version this WorkItem belongs to (null if not yet added to a version)")
    private Long versionId;

    @Field(label = "Deleted")
    private Boolean deleted;
}
