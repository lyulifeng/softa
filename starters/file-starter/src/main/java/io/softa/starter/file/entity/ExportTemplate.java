package io.softa.starter.file.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ExportTemplate Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(searchName = {"fileName"})
public class ExportTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(required = true)
    private String fileName;

    @Field
    private String sheetName;

    @Field(required = true)
    private String modelName;

    @Field
    private Boolean customFileTemplate;

    @Field(label = "File Template ID", fieldType = FieldType.FILE)
    private Long fileId;

    @Field(length = 256)
    private Filters filters;

    @Field
    private Orders orders;

    @Field(label = "Custom Export Handler", length = 128)
    private String customHandler;

    @Field
    private Boolean enableTranspose;

    @Field(fieldType = FieldType.ONE_TO_MANY, relatedField = "templateId")
    private List<ExportTemplateField> exportFields;
}
