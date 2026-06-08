package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.Ownership;

/**
 * DesignOptionSet Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Option Set",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        activeControl = true,
        businessKey = {"optionSetCode"}
)
public class DesignOptionSet extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Label", required = true, length = 64)
    private String label;

    @Field(label = "Option Set Code", required = true, length = 64)
    private String optionSetCode;

    @Field(label = "Option Items", fieldType = FieldType.ONE_TO_MANY,
            relatedModel = DesignOptionItem.class, relatedField = "optionSetCode")
    private List<DesignOptionItem> optionItems;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Ownership", description = "Ownership — PLATFORM_MAINTAINED / PLATFORM_DEFAULT / TENANT; "
            + "transparently transmitted to runtime sys_option_set on deploy (ADR-0009 #1)")
    private Ownership ownership;

    @Field(label = "Deleted")
    private Boolean deleted;
}
