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

/**
 * ExportTemplate Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Export Template", searchName = {"fileName"})
public class ExportTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "File Name", required = true, length = 64)
    private String fileName;

    @Field(label = "Sheet Name", length = 64)
    private String sheetName;

    @Field(label = "Model Name", required = true, length = 64)
    private String modelName;

    @Field(label = "Custom File Template")
    private Boolean customFileTemplate;

    @Field(label = "File Template ID")
    private Long fileId;

    @Field(label = "Filters")
    private Filters filters;

    @Field(label = "Orders")
    private Orders orders;

    @Field(label = "Custom Export Handler", length = 128)
    private String customHandler;

    @Field(label = "Enable Transpose")
    private Boolean enableTranspose;

    @Field(label = "Export Fields")
    private List<ExportTemplateField> exportFields;
}
