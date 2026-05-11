package io.softa.starter.referencedata.entity;

import java.io.Serial;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.entity.AuditableModel;
import io.softa.starter.referencedata.enums.Continent;

/**
 * Platform-level country/region master keyed by ISO 3166-1 alpha-2 code.
 * Read-only reference data — same rows serve all tenants. Seed loaded from
 * {@code data-system/CountryRegion.AllCountries.json} via metadata-starter's
 * {@code POST /SysPreData/loadPreSystemData}.
 *
 * <p>Natural key is {@link #code} (ISO 3166-1 alpha-2). Other tables that
 * reference a country (e.g. {@code SmsProviderRegion.regionCode},
 * {@code TenantInfo.defaultCountry}) store the alpha-2 string as a concept
 * FK — no relational FK constraint, just a documented convention validated
 * at the service layer.
 *
 * <p>The {@link #currencyCode} field is a string FK to {@code currency.code}
 * by the same convention. Mainland China (CN), Taiwan (TW), Hong Kong (HK),
 * Macau (MO) are distinct entries.
 */
@Data
@Schema(name = "CountryRegion")
@EqualsAndHashCode(callSuper = true)
public class CountryRegion extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "ISO 3166-1 alpha-2 code (CN/US/TW/...). Natural identifier; "
            + "use this for cross-table FK references and service lookups.")
    private String code;

    @Schema(description = "ISO 3166-1 standard English short name")
    private String name;

    @Schema(description = "ISO 3166-1 alpha-3 code (CHN/USA/TWN). Used by Stripe / SWIFT / "
            + "some government APIs that prefer 3-letter codes.")
    private String alpha3Code;

    @Schema(description = "ITU-T E.164 country dial code, digits only (no leading +). "
            + "Shared by NANP territories (US/CA/JM/... all '1') and Russia/Kazakhstan (both '7').")
    private String dialCode;

    @Schema(description = "Default ISO 4217 currency alpha-3 code; concept FK to currency.code")
    private String currencyCode;

    @Schema(description = "Continent (7-continent model)")
    private Continent continent;

    @Schema(description = "EEA / EU member flag — GDPR territorial scope, VAT reverse charge eligibility")
    private Boolean eea;

    @Schema(description = "Whether ISO 3166-2 subdivision data exists for this country in "
            + "country_subdivision. Runtime flag for 'is sub-region selector available?'")
    private Boolean hasSubdivisions;
}
