package io.softa.starter.tenant.provisioning;

import java.time.LocalDate;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.starter.tenant.enums.DataRegion;
import io.softa.starter.tenant.enums.TenantLifecycle;

/**
 * Request to provision a new tenant — the payload the standard TenantInfo create form posts to the
 * shadowed {@code /TenantInfo/createOne}. Field names mirror the TenantInfo metadata; the owned 1:1
 * version arrives inline under {@code subscriptionId} (the owner-side relation field). Unknown form
 * fields (e.g. status / audit) are ignored — a provisioned tenant is forced ACTIVE.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class ProvisionTenantRequest {

    private String name;
    /** Optional — slug-generated from the name when blank. */
    private String code;

    private Language defaultLanguage;
    private Timezone defaultTimezone;
    private String defaultCurrency;
    private String defaultCountry;
    private DataRegion dataRegion;

    /** The owned 1:1 version, created + linked as {@code TenantInfo.subscriptionId}. Optional. */
    private SubscriptionInput subscriptionId;

    /** Inline version fields — the {@code TenantSubscription} create payload. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class SubscriptionInput {
        /** Plan code (= plan.id); Free when blank. */
        private String planId;
        /** Lifecycle; SUBSCRIBED when null. */
        private TenantLifecycle lifecycle;
        /** Effective start date; today when null. */
        private LocalDate effectiveFrom;
        /** Expiry date; null = open-ended (lapses to Free at the tenant's local midnight after this date). */
        private LocalDate effectiveTo;
    }
}
