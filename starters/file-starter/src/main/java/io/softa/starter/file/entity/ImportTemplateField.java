package io.softa.starter.file.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.OnDelete;

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

    // onDelete = CASCADE: field mappings are owned by the template — deleting the template removes them.
    @Field(label = "Import Template ID", fieldType = FieldType.MANY_TO_ONE, relatedModel = ImportTemplate.class,
            onDelete = OnDelete.CASCADE)
    private Long templateId;

    @Field(required = true)
    private String fieldName;

    @Field
    private String customHeader;

    @Field
    private Integer sequence;

    @Field
    private Boolean required;

    @Field(length = 128)
    private String defaultValue;

    @Field(length = 256)
    private String description;
}
