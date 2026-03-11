package io.softa.starter.metadata.entity;

import io.softa.framework.orm.entity.AuditableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * TenantOptionSet Model
 */
@Data
@Schema(name = "TenantOptionSet")
@EqualsAndHashCode(callSuper = true)
public class TenantOptionSet extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "App ID")
    private Long appId;

    @Schema(description = "Option Set Name")
    private String name;

    @Schema(description = "Option Set Code")
    private String optionSetCode;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Active")
    private Boolean active;
}
