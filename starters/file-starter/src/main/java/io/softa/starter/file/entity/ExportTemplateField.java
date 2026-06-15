package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ExportTemplateField Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model
public class ExportTemplateField extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Export Template ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = ExportTemplate.class)
    private Long templateId;

    @Field(required = true)
    private String fieldName;

    @Field
    private String customHeader;

    @Field
    private Integer sequence;

    @Field(label = "Ignored In File")
    private Boolean ignored;
}
