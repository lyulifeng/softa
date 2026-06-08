package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.ViewType;

/**
 * DesignView Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Design View", idStrategy = IdStrategy.DISTRIBUTED_LONG, softDelete = true,
        businessKey = {"modelName", "code"})
public class DesignView extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "View Name", required = true, length = 64)
    private String name;

    @Field(label = "View Code", length = 64)
    private String code;

    @Field(label = "View Type", required = true)
    private ViewType type;

    @Field(label = "Sequence", required = true)
    private Integer sequence;

    @Field(label = "Structure", required = true)
    private JsonNode structure;

    @Field(label = "Default Filters", description = "View level default filter.")
    private JsonNode defaultFilter;

    @Field(label = "Default Order", description = "The default sorting condition at the view level.")
    private JsonNode defaultOrder;

    @Field(label = "Navigation ID")
    private Long navId;

    @Field(label = "Public View")
    private Boolean publicView;

    @Field(label = "Default View")
    private Boolean defaultView;

    @Field(label = "Deleted")
    private Boolean deleted;
}
