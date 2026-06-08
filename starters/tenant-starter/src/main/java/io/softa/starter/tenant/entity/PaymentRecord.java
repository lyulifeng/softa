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
        label = "Payment Record",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class PaymentRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Order ID")
    private Long orderId;

    @Field(label = "Payment Method")
    private PaymentMethod paymentMethod;

    @Field(label = "Payment Status")
    private PaymentStatus paymentStatus;

    @Field(label = "Paid Amount", length = 32, scale = 8)
    private BigDecimal paidAmount;

    @Field(label = "Paid At")
    private LocalDateTime paidAt;

    @Field(label = "Transaction ID", length = 64)
    private String transactionId;

    @Field(label = "Deleted")
    private Boolean deleted;
}
