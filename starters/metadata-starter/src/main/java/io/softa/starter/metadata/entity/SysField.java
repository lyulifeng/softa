package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.MaskingType;
import io.softa.framework.orm.enums.Ownership;
import io.softa.framework.orm.enums.WidgetType;

/**
 * SysField — metadata catalog row describing a Softa Field.
 *
 * <p>Self-described via {@code @Model} + per-field {@code @Field}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Field",
        businessKey = {"modelName", "fieldName"},
        description = "Metadata catalog of fields"
)
public class SysField extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Label")
    private String label;

    @Field(label = "Field Name", required = true)
    private String fieldName;

    @Field(label = "Column Name")
    private String columnName;

    @Field(label = "Model Name", required = true)
    private String modelName;

    @Field(label = "Model ID")
    private Long modelId;

    @Field(label = "Description")
    private String description;

    @Field(label = "Field Type", required = true)
    private FieldType fieldType;

    @Field(label = "Option Set Code")
    private String optionSetCode;

    @Field(label = "Related Model")
    private String relatedModel;

    @Field(label = "Related Field")
    private String relatedField;

    @Field(label = "Join Model")
    private String joinModel;

    @Field(label = "Join Model Left Key")
    private String joinLeft;

    @Field(label = "Join Model Right Key")
    private String joinRight;

    @Field(label = "Cascaded Field")
    private String cascadedField;

    @Field(label = "Filters")
    private String filters;

    @Field(label = "Default Value")
    private String defaultValue;

    @Field(label = "Length")
    private Integer length;

    @Field(label = "Scale")
    private Integer scale;

    @Field(label = "Is Required")
    private Boolean required;

    @Field(label = "Is Readonly")
    private Boolean readonly;

    @Field(label = "Hidden")
    private Boolean hidden;

    @Field(label = "Translatable")
    private Boolean translatable;

    @Field(label = "Non Copyable")
    private Boolean nonCopyable;

    @Field(label = "Unsearchable")
    private Boolean unsearchable;

    @Field(label = "Is Computed")
    private Boolean computed;

    @Field(label = "Expression")
    private String expression;

    @Field(label = "Dynamic Field")
    private Boolean dynamic;

    @Field(label = "Is Encrypted")
    private Boolean encrypted;

    @Field(label = "Masking Type")
    private MaskingType maskingType;

    @Field(label = "Widget Type")
    private WidgetType widgetType;

    @Field(label = "Ownership")
    private Ownership ownership;
}
