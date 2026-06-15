package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;

/**
 * Data-residency region a tenant's data is hosted in
 * ({@code TenantInfo.dataRegion}).
 *
 * <p>A platform-fixed vocabulary (code-fixed, like {@code TenantStatus}) rather
 * than an open infrastructure value: residency is a contractual / compliance
 * boundary the platform offers, so it is validated and version-controlled in
 * code. Add a constant when the platform onboards a new residency region.
 *
 * <p>NOTE: this is the <b>initial</b> region set — confirm/extend it against the
 * actual platform offering (e.g. add {@code CN}, {@code UK}, {@code AU} as
 * residency zones are launched). The {@code @JsonValue} code is the stored
 * value; keep it stable once shipped.
 */
@Getter
@AllArgsConstructor
@OptionSet(description = "Data-residency region a tenant's data is hosted in")
public enum DataRegion {

    @OptionItem(label = "United States")
    US("US"),

    @OptionItem(label = "European Union")
    EU("EU"),

    @OptionItem(label = "Asia Pacific")
    APAC("APAC"),
    ;

    @JsonValue
    private final String code;
}
