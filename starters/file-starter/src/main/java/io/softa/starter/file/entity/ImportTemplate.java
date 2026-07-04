package io.softa.starter.file.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.starter.file.enums.ImportRule;

/**
 * ImportTemplate Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model
public class ImportTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Template Name", required = true)
    private String name;

    @Field(required = true)
    private String modelName;

    @Field(required = true)
    private ImportRule importRule;

    @Field
    private List<String> uniqueConstraints;

    @Field(label = "Ignore Empty Value")
    private Boolean ignoreEmpty;

    @Field(label = "Skip Abnormal Data")
    private Boolean skipException;

    @Field(label = "Custom Import Handler", length = 128)
    private String customHandler;

    @Field(label = "Synchronous Import")
    private Boolean syncImport;

    @Field(label = "Include Import Description")
    private Boolean includeDescription;

    @Field(length = 1000)
    private String description;

    @Field(fieldType = FieldType.ONE_TO_MANY, relatedField = "templateId")
    private List<ImportTemplateField> importFields;
}
