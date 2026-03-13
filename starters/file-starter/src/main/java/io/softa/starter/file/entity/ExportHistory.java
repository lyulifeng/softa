package io.softa.starter.file.entity;

import java.io.Serial;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.entity.AuditableModel;

/**
 * ExportHistory Model
 */
@Data
@Schema(name = "ExportHistory")
@EqualsAndHashCode(callSuper = true)
public class ExportHistory extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Tenant ID")
    private Long tenantId;

    @Schema(description = "Template ID")
    private Long templateId;

    @Schema(description = "Model Name")
    private String modelName;

    @Schema(description = "Exported File ID")
    private Long exportedFileId;

    @Schema(description = "Total Rows")
    private Integer totalRows;

    @Schema(description = "Duration in seconds")
    private Double duration;

    @Schema(description = "Deleted")
    private Boolean deleted;
}