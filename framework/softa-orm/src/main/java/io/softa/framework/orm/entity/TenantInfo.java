package io.softa.framework.orm.entity;

import java.io.Serial;
import java.time.LocalDateTime;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.framework.orm.annotation.Field;
import io.softa.framework.orm.annotation.Model;
import io.softa.framework.orm.enums.IdStrategy;
import io.softa.framework.orm.enums.TenantLifecycle;
import io.softa.framework.orm.enums.TenantStatus;

/**
 * TenantInfo Model
 */
@Data
@EqualsAndHashCode(callSuper = true)
@Model(
        label = "Tenant Info",
        idStrategy = IdStrategy.DISTRIBUTED_LONG,
        softDelete = true
)
public class TenantInfo extends AuditableModel {

    @Serial
    private static final long serialVersionUID = 1L;

    @Field(label = "ID")
    private Long id;

    @Field(label = "Name", length = 64)
    private String name;

    @Field(label = "Code", length = 64)
    private String code;

    @Field(label = "Status")
    private TenantStatus status;

    @Field(label = "Lifecycle Stage")
    private TenantLifecycle lifecycle;

    @Field(label = "Activated Time")
    private LocalDateTime activatedTime;

    @Field(label = "Suspended Time")
    private LocalDateTime suspendedTime;

    @Field(label = "Closed Time")
    private LocalDateTime closedTime;

    @Field(label = "Default Language")
    private Language defaultLanguage;

    @Field(label = "Default Timezone")
    private Timezone defaultTimezone;

    @Field(label = "Default Currency", length = 4,
            description = "Default billing/display currency for this tenant. ISO 4217 alpha-3 code "
                    + "(USD/CNY/EUR/...); concept FK to currency.code in reference-data-starter. "
                    + "Used as the seed default for new invoices/orders.")
    private String defaultCurrency;

    @Field(label = "Default Country", length = 4,
            description = "Default country/region for this tenant. ISO 3166-1 alpha-2 code "
                    + "(CN/US/TW/...); concept FK to country_region.code in reference-data-starter. "
                    + "Used as the seed default for new users, billing addresses, and locale hints.")
    private String defaultCountry;

    @Field(label = "Data Region", length = 64)
    private String dataRegion;

    @Field(label = "Plan ID")
    private Long planId;

    @Field(label = "Subscription ID")
    private Long subscriptionId;

    @Field(label = "Deleted")
    private Boolean deleted;
}
