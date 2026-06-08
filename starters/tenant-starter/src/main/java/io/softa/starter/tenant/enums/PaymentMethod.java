package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.orm.annotation.OptionItem;
import io.softa.framework.orm.annotation.OptionSet;

/**
 * WeChat, AliPay, Stripe, PayPal, ApplePay, OfflinePayment,
 */
@Getter
@AllArgsConstructor
@OptionSet(label = "Payment Method")
public enum PaymentMethod {
    @OptionItem(label = "WeChat Pay")
    WE_CHAT("WeChat"),
    @OptionItem(label = "Alipay")
    ALI_PAY("AliPay"),
    @OptionItem(label = "Stripe")
    STRIPE("Stripe"),
    @OptionItem(label = "PayPal")
    PAY_PAL("PayPal"),
    @OptionItem(label = "Apple Pay")
    APPLE_PAY("ApplePay"),
    @OptionItem(label = "Offline Payment")
    OFFLINE_PAYMENT("OfflinePayment"),
    ;

    @JsonValue
    private final String type;
}
