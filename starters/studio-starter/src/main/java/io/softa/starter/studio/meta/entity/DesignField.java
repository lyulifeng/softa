package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.MaskingType;
import io.softa.framework.orm.enums.Ownership;
import io.softa.framework.orm.enums.WidgetType;

/**
 * DesignField Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Field",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        businessKey = {"modelName", "fieldName"},
        displayName = {"label"}
)
public class DesignField extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Model ID")
    private Long modelId;

    @Field(label = "Label", required = true, length = 64)
    private String label;

    @Field(label = "Field Name", required = true, length = 64)
    private String fieldName;

    @Field(label = "Column Name", length = 64)
    private String columnName;

    @Field(label = "Model Name", required = true, length = 64)
    private String modelName;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Field Type", required = true)
    private FieldType fieldType;

    @Field(label = "Option Set Code", length = 64)
    private String optionSetCode;

    @Field(label = "Related Model", length = 64)
    private String relatedModel;

    @Field(label = "Related Field", length = 64)
    private String relatedField;

    @Field(label = "Join Model", length = 64)
    private String joinModel;

    @Field(label = "Join Model Left Key", length = 64)
    private String joinLeft;

    @Field(label = "Join Model Right Key", length = 64)
    private String joinRight;

    @Field(label = "Cascaded Field", length = 256)
    private String cascadedField;

    @Field(label = "Filters", length = 256)
    private String filters;

    @Field(label = "Default Value", length = 256)
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

    @Field(label = "Expression", length = 20000)
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

    @Field(label = "Deleted")
    private Boolean deleted;
}
