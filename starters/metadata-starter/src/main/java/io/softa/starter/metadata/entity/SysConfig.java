package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;

/**
 * SysConfig Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Config",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        activeControl = true,
        businessKey = {"code"},
        displayName = {"name"},
        searchName = {"name"}
)
public class SysConfig extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Name", required = true, length = 64)
    private String name;

    @Field(label = "Code", required = true, length = 64)
    private String code;

    @Field(label = "Value", required = true)
    private JsonNode value;

    @Field(label = "Value Type", length = 64)
    private String valueType;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;
}
