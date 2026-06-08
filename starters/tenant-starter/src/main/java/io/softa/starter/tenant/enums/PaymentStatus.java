package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * Unpaid, Paid, Failed, Canceled, Refunded
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Payment Status")
public enum PaymentStatus {
    @OptionItem(label = "Unpaid")
    UNPAID("Unpaid"),
    @OptionItem(label = "Paid")
    PAID("Paid"),
    @OptionItem(label = "Payment Failed")
    FAILED("Failed"),
    @OptionItem(label = "Payment Canceled")
    CANCELED("Canceled"),
    @OptionItem(label = "Refunded")
    REFUNDED("Refunded"),
    ;

    @JsonValue
    private final String status;
}
