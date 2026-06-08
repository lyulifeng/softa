package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ImportTemplateField Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Import Template Fields")
public class ImportTemplateField extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Import Template ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = ImportTemplate.class)
    private Long templateId;

    @Field(label = "Field Name", required = true, length = 64)
    private String fieldName;

    @Field(label = "Custom Header", length = 64)
    private String customHeader;

    @Field(label = "Sequence")
    private Integer sequence;

    @Field(label = "Required")
    private Boolean required;

    @Field(label = "Default Value", length = 128)
    private String defaultValue;

    @Field(label = "Description", length = 256)
    private String description;
}
