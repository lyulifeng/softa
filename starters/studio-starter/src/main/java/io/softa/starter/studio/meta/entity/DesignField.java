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

    @Field(required = true)
    private String label;

    @Field(required = true)
    private String fieldName;

    @Field
    private String columnName;

    @Field(required = true)
    private String modelName;

    @Field(length = 256)
    private String description;

    @Field(required = true)
    private FieldType fieldType;

    @Field
    private String optionSetCode;

    @Field
    private String relatedModel;

    @Field
    private String relatedField;

    @Field
    private String joinModel;

    @Field(label = "Join Model Left Key")
    private String joinLeft;

    @Field(label = "Join Model Right Key")
    private String joinRight;

    @Field(length = 256)
    private String cascadedField;

    @Field(length = 256)
    private String filters;

    @Field(length = 256)
    private String defaultValue;

    @Field
    private Integer length;

    @Field
    private Integer scale;

    @Field(label = "Is Required")
    private Boolean required;

    @Field(label = "Is Readonly")
    private Boolean readonly;

    @Field
    private Boolean hidden;

    @Field
    private Boolean translatable;

    // Initialized to true (the column is NOT NULL DEFAULT 1) so hand-constructed
    // instances never insert NULL.
    @Field(defaultValue = "true")
    private Boolean copyable = Boolean.TRUE;

    @Field
    private Boolean unsearchable;

    @Field(label = "Is Computed")
    private Boolean computed;

    @Field(length = 20000)
    private String expression;

    @Field(label = "Dynamic Field")
    private Boolean dynamic;

    @Field(label = "Is Encrypted")
    private Boolean encrypted;

    @Field
    private MaskingType maskingType;

    @Field
    private WidgetType widgetType;

    @Field
    private Ownership ownership;

    @Field
    private Boolean deleted;
}
