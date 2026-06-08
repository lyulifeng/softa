package io.softa.starter.studio.release.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.DatabaseType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.studio.release.enums.DesignAppStatus;

/**
 * DesignApp Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Design App", idStrategy = IdStrategy.DISTRIBUTED_LONG, displayName = "appName")
public class DesignApp extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio", required = true)
    private Long portfolioId;

    @Field(label = "Owner")
    private Long ownerId;

    @Field(label = "App Name", required = true, length = 64)
    private String appName;

    @Field(label = "App Code", required = true, length = 64)
    private String appCode;

    @Field(label = "App Type", length = 64)
    private String appType;

    @Field(label = "Database Type")
    private DatabaseType databaseType;

    @Field(label = "Package Name", description = "Fill in when you need to generate code, the model in the same App belongs to the same Module.", length = 64)
    private String packageName;

    @Field(label = "App Status")
    private DesignAppStatus status;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Deleted")
    private Boolean deleted;
}
