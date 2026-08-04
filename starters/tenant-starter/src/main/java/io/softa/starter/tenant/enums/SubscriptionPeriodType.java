package io.softa.starter.tenant.enums;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonValue;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import io.softa.framework.base.enums.OptionItemTone;

/**
 * What a subscription period sells — whether the tenant paid for it.
 *
 * <p><b>Zero effect on entitlement.</b> A {@code TRIAL Enterprise} period and a {@code PAID Enterprise}
 * period grant exactly the same modules; no authorization logic reads this field. Trial is not a
 * feature-reduced tier, it is the same thing for a limited time.
 *
 * <p>It exists because "was this period paid for" cannot be derived: not from the dates (the same span
 * could be either), not from the plan (the same plan can be trialled or bought), and there is no price
 * column (payments are out of scope). It is the one stored fact in a design that otherwise derives
 * everything from dates. Three consumers:
 *
 * <ol>
 *   <li><b>Expiry reminder wording</b> — trial expiry pitches an upgrade, paid expiry pitches a renewal;
 *       two separate mail templates.</li>
 *   <li><b>Projected display status</b> — decides {@code TRIAL} vs {@code PAID} on the owning
 *       subscription row.</li>
 *   <li><b>Write guard</b> — {@code TRIAL} is only allowed above the floor plan; trialling the floor
 *       makes no sense.</li>
 * </ol>
 */
@Getter
@OptionSet(description = "Whether a subscription period was paid for or is a trial")
public enum SubscriptionPeriodType {

    @OptionItem(itemTone = OptionItemTone.WARNING)
    TRIAL("Trial"),

    @OptionItem(itemTone = OptionItemTone.INFO)
    PAID("Paid");

    @JsonValue
    private final String code;

    SubscriptionPeriodType(String code) {
        this.code = code;
    }
}
