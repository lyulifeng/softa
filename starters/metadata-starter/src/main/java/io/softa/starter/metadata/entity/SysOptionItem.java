package io.softa.starter.metadata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.OptionItemIcon;
import io.softa.framework.orm.enums.OptionItemTone;
import io.softa.framework.orm.enums.Ownership;

/**
 * SysOptionItem — metadata catalog row describing an OptionSet member.
 *
 * <p>Self-described via {@code @Model} + per-field {@code @Field}.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "System Option Item",
        activeControl = true,
        businessKey = {"optionSetCode", "itemCode"},
        description = "Metadata catalog of option items"
)
public class SysOptionItem extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Option Set ID")
    private Long optionSetId;

    @Field(label = "Option Set Code", required = true)
    private String optionSetCode;

    @Field(label = "Sequence")
    private Integer sequence;

    @Field(label = "Item Code", required = true)
    private String itemCode;

    @Field(label = "Label", required = true)
    private String label;

    @Field(label = "Parent Item Code")
    private String parentItemCode;

    @Field(label = "Item Tone")
    private OptionItemTone itemTone;

    @Field(label = "Item Icon")
    private OptionItemIcon itemIcon;

    @Field(label = "Description")
    private String description;

    @Field(label = "Ownership")
    private Ownership ownership;

    @Field(label = "Active")
    private Boolean active;
}
