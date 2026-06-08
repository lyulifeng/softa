package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.ViewType;

/**
 * SysView Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "System View", businessKey = {"modelName", "code"})
public class SysView extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Code", length = 64)
    private String code;

    @Field(label = "Type", required = true)
    private ViewType type;

    @Field(label = "Sequence")
    private Integer sequence;

    @Field(label = "Structure", required = true)
    private JsonNode structure;

    @Field(label = "Default Filter")
    private Filters defaultFilter;

    @Field(label = "Default Order")
    private Orders defaultOrder;

    @Field(label = "Nav ID")
    private Long navId;

    @Field(label = "Public View")
    private Boolean publicView;

    @Field(label = "Default View")
    private Boolean defaultView;

    @Field(label = "Deleted")
    private Boolean deleted;
}
