package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * DesignNavigation Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Navigation",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        businessKey = {"code"}
)
public class DesignNavigation extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Type", required = true, length = 64)
    private String type;

    @Field(label = "Code", required = true, length = 64)
    private String code;

    @Field(label = "Model Name", length = 256)
    private String modelName;

    @Field(label = "Parent Navigation")
    private Long parentId;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Default filters", description = "The default filters at the menu level.", length = 256)
    private String filter;

    @Field(label = "Deleted")
    private Boolean deleted;
}
