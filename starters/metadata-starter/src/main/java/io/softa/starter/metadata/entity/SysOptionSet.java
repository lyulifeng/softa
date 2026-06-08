package io.softa.starter.metadata.entity;

import java.io.Serial;
import java.util.List;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.Ownership;

/**
 * SysOptionSet — metadata catalog row describing an OptionSet (enum domain).
 *
 * <p>Self-described via {@code @Model} + per-field {@code @Field}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Option Set",
        activeControl = true,
        businessKey = {"optionSetCode"},
        description = "Metadata catalog of option sets"
)
public class SysOptionSet extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Label", required = true)
    private String label;

    @Field(label = "Option Set Code", required = true)
    private String optionSetCode;

    @Field(label = "Description")
    private String description;

    @Field(label = "Ownership")
    private Ownership ownership;

    @Field(label = "Active")
    private Boolean active;

    /**
     * One-to-many to {@link SysOptionItem} (joins on business key {@code optionSetCode}, not id).
     * Has NO {@code sys_option_set} column — SysCatalog and the DDL builder both
     * exclude X-to-many — but is emitted as a {@code sys_field} row. Populated in
     * memory by {@code OptionManager}.
     */
    @Field(label = "Option Items", fieldType = FieldType.ONE_TO_MANY, relatedModel = SysOptionItem.class, relatedField = "optionSetCode")
    private List<SysOptionItem> optionItems;
}
