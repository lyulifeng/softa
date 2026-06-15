package io.softa.starter.referencedata.entity;

import java.io.Serial;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Index;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;

/**
 * ISO 3166-2 country subdivisions (provinces, states, prefectures, etc.).
 * Platform-level reference data; {@link #countryCode} is a reference-by-code FK
 * to {@code country_region.code} (ADR-0017): the column stores the ISO alpha-2
 * code, joins/validates against the country master, and renders a picker.
 *
 * <p>Table and entity are created in this PR but <b>data is not seeded</b>
 * — populated when address/tax/shipping features land. {@code CountryRegion}
 * exposes a {@code hasSubdivisions} boolean as the runtime indicator of
 * whether this table has data for a given country.
 *
 * <p>Hierarchical subdivisions (e.g. Chinese {@code 省→市} or Japanese
 * {@code 都道府県→市}) use {@link #parentCode} as a reference-by-code FK to
 * this table's own {@code code} (a self-relation).
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        businessKey = {"code"},
        description = "ISO 3166-2 country subdivisions"
)
@Index(indexName = "uk_code", fields = {"code"}, unique = true)
@Index(indexName = "idx_country", fields = {"countryCode"})
@Index(indexName = "idx_parent", fields = {"parentCode"})
public class CountrySubdivision extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(required = true, fieldType = FieldType.MANY_TO_ONE,
            relatedModel = CountryRegion.class, relatedField = "code",
            description = "Owning country — reference-by-code FK to country_region.code (ISO 3166-1 alpha-2)")
    private String countryCode;

    @Field(required = true, length = 10, copyable = false,
            description = "ISO 3166-2 full code (CN-31 / US-CA / JP-13); natural key")
    private String code;

    @Field(required = true, length = 100,
            description = "English name")
    private String name;

    @Field(fieldType = FieldType.MANY_TO_ONE,
            relatedModel = CountrySubdivision.class, relatedField = "code",
            description = "Parent subdivision for hierarchical regions — reference-by-code FK to "
                    + "country_subdivision.code (self-relation); null for top-level")
    private String parentCode;

    @Field(length = 20,
            description = "Subdivision type — province / state / prefecture / region / municipality / county")
    private String type;
}
