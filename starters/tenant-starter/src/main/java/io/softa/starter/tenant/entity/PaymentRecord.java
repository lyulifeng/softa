package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.tenant.enums.PaymentMethod;
import io.softa.starter.tenant.enums.PaymentStatus;

/**
 * PaymentRecord Model
 */
@Data
@Schema(name = "PaymentRecord")
@EqualsAndHashCode(callSuper = true)
public class PaymentRecord extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "Order ID")
    private Long orderId;

    @Schema(description = "Payment Method")
    private PaymentMethod paymentMethod;

    @Schema(description = "Payment Status")
    private PaymentStatus paymentStatus;

    @Schema(description = "Paid Amount")
    private BigDecimal paidAmount;

    @Schema(description = "Paid At")
    private LocalDateTime paidAt;

    @Schema(description = "Transaction ID")
    private String transactionId;

    @Schema(description = "Deleted")
    private Boolean deleted;
}