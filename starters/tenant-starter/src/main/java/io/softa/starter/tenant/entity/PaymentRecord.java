package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.tenant.enums.PaymentMethod;
import io.softa.starter.tenant.enums.PaymentStatus;

/**
 * PaymentRecord Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true,
        copyable = false
)
public class PaymentRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Order ID")
    private Long orderId;

    @Field
    private PaymentMethod paymentMethod;

    @Field
    private PaymentStatus paymentStatus;

    @Field
    private BigDecimal paidAmount;

    @Field
    private LocalDateTime paidAt;

    @Field(label = "Transaction ID")
    private String transactionId;

    @Field
    private Boolean deleted;
}
