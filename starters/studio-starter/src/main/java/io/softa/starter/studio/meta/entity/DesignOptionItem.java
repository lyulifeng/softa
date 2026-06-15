package io.softa.starter.studio.meta.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.base.enums.OptionItemIcon;
import io.softa.framework.base.enums.OptionItemTone;
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

    @Field(required = true)
    private String optionSetCode;

    @Field(required = true)
    private Integer sequence;

    @Field(required = true)
    private String itemCode;

    @Field(required = true)
    private String label;

    @Field
    private String parentItemCode;

    @Field
    private OptionItemTone itemTone;

    @Field
    private OptionItemIcon itemIcon;

    @Field(length = 256)
    private String description;

    @Field
    private Boolean active;

    @Field
    private Ownership ownership;

    @Field
    private Boolean deleted;
}
