package io.softa.starter.tenant.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.entity.AuditableModel;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.starter.referencedata.entity.CountryRegion;
import io.softa.starter.referencedata.entity.Currency;
import io.softa.starter.tenant.enums.DataRegion;
import io.softa.starter.tenant.enums.TenantLifecycle;
import io.softa.starter.tenant.enums.TenantStatus;

/**
 * TenantInfo Model — the platform tenant registry. Lives in tenant-starter so it can
 * reference the reference-data master tables by code (ADR-0017). The framework only
 * depends on the {@code TenantInfoService} SPI (active ids / isTenantActive / deactivate),
 * never on this entity.
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class TenantInfo extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field
    private String name;

    @Field
    private String code;

    @Field
    private TenantStatus status;

    @Field(label = "Lifecycle Stage")
    private TenantLifecycle lifecycle;

    @Field(copyable = false)
    private LocalDateTime activatedTime;

    @Field(copyable = false)
    private LocalDateTime suspendedTime;

    @Field(copyable = false)
    private LocalDateTime closedTime;

    @Field
    private Language defaultLanguage;

    @Field
    private Timezone defaultTimezone;

    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = Currency.class, relatedField = "code",
            description = "Default billing/display currency — reference-by-code FK to currency.code "
                    + "(ISO 4217 alpha-3). Seed default for new invoices/orders.")
    private String defaultCurrency;

    @Field(fieldType = FieldType.MANY_TO_ONE, relatedModel = CountryRegion.class, relatedField = "code",
            description = "Default country/region — reference-by-code FK to country_region.code "
                    + "(ISO 3166-1 alpha-2). Seed default for new users, billing addresses, locale hints.")
    private String defaultCountry;

    @Field(description = "Data-residency region this tenant's data is hosted in (platform-fixed set)")
    private DataRegion dataRegion;

    @Field(label = "Plan ID")
    private Long planId;

    @Field(label = "Subscription ID")
    private Long subscriptionId;

    @Field
    private Boolean deleted;
}
