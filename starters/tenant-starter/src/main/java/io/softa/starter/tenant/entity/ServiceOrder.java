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

    @Field(length = 32)
    private String orderNumber;

    @Field
    private OrderStatus orderStatus;

    @Field
    private BigDecimal amount;

    @Field(length = 1000)
    private String notes;

    @Field
    private Boolean deleted;
}
