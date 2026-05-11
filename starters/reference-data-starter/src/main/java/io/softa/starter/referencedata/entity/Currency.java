package io.softa.starter.referencedata.entity;

import io.softa.framework.orm.entity.AuditableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * Platform-level currency master keyed by ISO 4217 alpha-3 code.
 * Read-only reference data — same rows serve all tenants. Seed loaded from
 * {@code data-system/Currency.AllCurrencies.json}.
 *
 * <p>Natural key is {@link #code} (ISO 4217 alpha-3). The
 * {@link #decimalPlaces} field is <b>critical</b> for monetary arithmetic
 * — JPY/KRW use 0 fraction digits, USD/EUR/CNY use 2, BHD/KWD use 3.
 * Mismatching these breaks currency rendering and rounding for that country.
 */
@Data
@Schema(name = "Currency")
@EqualsAndHashCode(callSuper = true)
public class Currency extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "ISO 4217 alpha-3 code (USD/CNY/EUR/...). Natural key.")
    private String code;

    @Schema(description = "ISO 4217 numeric code, 3 digits with leading zeros preserved as String "
            + "(840 USD / 156 CNY / 048 BHD).")
    private String numericCode;

    @Schema(description = "English name, e.g. 'US Dollar'")
    private String name;

    @Schema(description = "Unicode display symbol ($ / ¥ / € / ₹ / £ / ...). Some currencies "
            + "share symbols (US$ and HK$ are both '$'); UI should prefer code disambiguation "
            + "in mixed contexts.")
    private String symbol;

    @Schema(description = "ISO 4217 fraction digits — 0 for JPY/KRW, 2 for USD/EUR/CNY, "
            + "3 for BHD/KWD/IQD. CRITICAL for monetary arithmetic; must match spec exactly.")
    private Integer decimalPlaces;
}
