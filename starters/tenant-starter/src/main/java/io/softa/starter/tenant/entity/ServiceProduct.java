package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.tenant.enums.ServiceCategory;

/**
 * ServiceProduct Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Service Product",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        activeControl = true
)
public class ServiceProduct extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Service Name", length = 32)
    private String name;

    @Field(label = "Service Description", length = 20000)
    private String description;

    @Field(label = "Service Category")
    private ServiceCategory category;

    @Field(label = "Price($)", length = 32, scale = 8)
    private BigDecimal price;

    @Field(label = "Service Duration(mins)")
    private Integer duration;

    @Field(label = "Active")
    private Boolean active;

    @Field(label = "Deleted")
    private Boolean deleted;
}
