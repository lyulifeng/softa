package io.softa.starter.file.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.file.enums.ImportRule;

/**
 * ImportTemplate Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(label = "Import Template")
public class ImportTemplate extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Tenant ID")
    private Long tenantId;

    @Field(label = "Template Name", required = true, length = 64)
    private String name;

    @Field(label = "Model Name", required = true, length = 64)
    private String modelName;

    @Field(label = "Import Rule", required = true)
    private ImportRule importRule;

    @Field(label = "Unique Constraints")
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

    @Field(label = "Description", length = 1000)
    private String description;

    @Field(label = "Import Field List")
    private List<ImportTemplateField> importFields;
}
