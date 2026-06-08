package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * PendingPayment, InProgress, Completed, Cancelled, Refunded
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Order Status")
public enum OrderStatus {
    @OptionItem(label = "Pending Payment")
    PENDING_PAYMENT("PendingPayment"),
    @OptionItem(label = "In Progress")
    IN_PROGRESS("InProgress"),
    @OptionItem(label = "Completed")
    COMPLETED("Completed"),
    @OptionItem(label = "Cancelled")
    CANCELLED("Cancelled"),
    @OptionItem(label = "Refunded")
    REFUNDED("Refunded"),
    ;

    @JsonValue
    private final String status;
}
