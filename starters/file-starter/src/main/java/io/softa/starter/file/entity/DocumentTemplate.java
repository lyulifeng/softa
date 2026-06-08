package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.file.enums.DocumentTemplateType;

/**
 * DocumentTemplate Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Document Template")
public class DocumentTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Model Name", length = 64)
    private String modelName;

    @Field(label = "File Name", length = 128)
    private String fileName;

    @Field(label = "Template Type")
    private DocumentTemplateType templateType;

    @Field(label = "File Template ID")
    private Long fileId;

    @Field(label = "HTML Template Content", length = 20000)
    private String htmlTemplate;

    @Field(label = "Convert To PDF")
    private Boolean convertToPdf;

    @Field(label = "Description", length = 256)
    private String description;
}
