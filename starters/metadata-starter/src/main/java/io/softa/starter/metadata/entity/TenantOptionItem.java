package io.softa.starter.metadata.entity;

import io.softa.framework.orm.entity.AuditableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * TenantOptionItem Model
 */
@Data
@Schema(name = "TenantOptionItem")
@EqualsAndHashCode(callSuper = true)
public class TenantOptionItem extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "App ID")
    private Long appId;

    @Schema(description = "Option Set ID")
    private Long optionSetId;

    @Schema(description = "Option Set Code")
    private String optionSetCode;

    @Schema(description = "Parent Item ID")
    private Long parentItemId;

    @Schema(description = "Sequence")
    private Integer sequence;

    @Schema(description = "Item Code")
    private String itemCode;

    @Schema(description = "Item Name")
    private String itemName;

    @Schema(description = "Item Color")
    private String itemColor;

    @Schema(description = "Description")
    private String description;

    @Schema(description = "Active")
    private Boolean active;
}
