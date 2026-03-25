package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;
import tools.jackson.databind.JsonNode;

import io.softa.framework.orm.entity.AuditableModel;

/**
 * DesignConfig Model
 */
@Data
@Schema(name = "DesignConfig")
@EqualsAndHashCode(callSuper = true)
public class DesignConfig extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "App ID")
    private Long appId;

    @Schema(description = "Name")
    private String name;

    @Schema(description = "Code")
    private String code;

    @Schema(description = "Value")
    private JsonNode value;

    @Schema(description = "Value Data Type")
    private String valueType;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Active")
    private Boolean active;

    @Schema(description = "Deleted")
    private Boolean deleted;
}