package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.math.BigDecimal;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.tenant.enums.OrderStatus;

/**
 * ServiceOrder Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Service Order",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class ServiceOrder extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "User")
    private Long userId;

    @Field(label = "Service Product")
    private Long serviceId;

    @Field(label = "Order Number", length = 32)
    private String orderNumber;

    @Field(label = "Order Status")
    private OrderStatus orderStatus;

    @Field(label = "Amount", length = 32, scale = 8)
    private BigDecimal amount;

    @Field(label = "Notes", length = 1000)
    private String notes;

    @Field(label = "Deleted")
    private Boolean deleted;
}
