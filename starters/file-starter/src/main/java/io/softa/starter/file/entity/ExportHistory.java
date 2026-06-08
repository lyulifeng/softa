package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ExportHistory Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Export History")
public class ExportHistory extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Template ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = ExportTemplate.class)
    private Long templateId;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "Exported File ID", required = true)
    private Long exportedFileId;

    @Field(label = "Total Rows")
    private Integer totalRows;

    @Field(label = "Duration in seconds")
    private Double duration;

    @Field(label = "Deleted")
    private Boolean deleted;
}
