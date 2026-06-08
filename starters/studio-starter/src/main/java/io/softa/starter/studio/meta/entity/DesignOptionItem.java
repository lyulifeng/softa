package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.OptionItemIcon;
import io.softa.framework.orm.enums.OptionItemTone;
import io.softa.framework.orm.enums.Ownership;

/**
 * DesignOptionItem Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Design Option Items",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        businessKey = {"optionSetCode", "itemCode"},
        displayName = {"itemCode", "label"},
        defaultOrder = {"optionSetCode", "sequence"}
)
public class DesignOptionItem extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Portfolio")
    private Long portfolioId;

    @Field(label = "App ID")
    private Long appId;

    @Field(label = "Option Set ID")
    private Long optionSetId;

    @Field(label = "Option Set Code", required = true, length = 64)
    private String optionSetCode;

    @Field(label = "Sequence", required = true)
    private Integer sequence;

    @Field(label = "Item Code", required = true, length = 64)
    private String itemCode;

    @Field(label = "Label", required = true, length = 64)
    private String label;

    @Field(label = "Parent Item Code", length = 64)
    private String parentItemCode;

    @Field(label = "Item Tone")
    private OptionItemTone itemTone;

    @Field(label = "Item Icon")
    private OptionItemIcon itemIcon;

    @Field(label = "Description", length = 256)
    private String description;

    @Field(label = "Active")
    private Boolean active;

    @Field(label = "Ownership")
    private Ownership ownership;

    @Field(label = "Deleted")
    private Boolean deleted;
}
