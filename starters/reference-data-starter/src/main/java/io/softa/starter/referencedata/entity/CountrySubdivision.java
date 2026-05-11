package io.softa.starter.referencedata.entity;

import io.softa.framework.orm.entity.AuditableModel;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.io.Serial;

/**
 * ISO 3166-2 country subdivisions (provinces, states, prefectures, etc.).
 * Platform-level reference data; concept FK by {@link #countryCode} to
 * {@code country_region.code}.
 *
 * <p>Table and entity are created in this PR but <b>data is not seeded</b>
 * — populated when address/tax/shipping features land. {@code CountryRegion}
 * exposes a {@code hasSubdivisions} boolean as the runtime indicator of
 * whether this table has data for a given country.
 *
 * <p>Hierarchical subdivisions (e.g. Chinese {@code 省→市} or Japanese
 * {@code 都道府県→市}) use {@link #parentCode} to link to the parent
 * subdivision's {@code code}.
 */
@Data
@Schema(name = "CountrySubdivision")
@EqualsAndHashCode(callSuper = true)
public class CountrySubdivision extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "ID")
    private Long id;

    @Schema(description = "ISO 3166-1 alpha-2 country code; concept FK to country_region.code")
    private String countryCode;

    @Schema(description = "ISO 3166-2 full code (CN-31 / US-CA / JP-13). Natural key.")
    private String code;

    @Schema(description = "English name")
    private String name;

    @Schema(description = "Parent subdivision code for hierarchical regions; null for top-level "
            + "(e.g. CN-31 has no parent; a CN city under it would have parentCode = CN-31)")
    private String parentCode;

    @Schema(description = "Subdivision type — province / state / prefecture / region / municipality / county. "
            + "Free-text by convention; UI can use it to pick a localized label.")
    private String type;
}
