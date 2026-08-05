package io.softa.starter.tenant.provisioning;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import lombok.Data;

import io.softa.framework.base.enums.Language;
import io.softa.framework.base.enums.Timezone;
import io.softa.starter.tenant.enums.DataRegion;
import io.softa.starter.tenant.service.SubscriptionPeriodPatch;

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
        /**
         * Periods to sell along with the tenant, so a customer who is buying Pro on day one is recorded in
         * one submit instead of being created on the floor plan and upgraded afterwards. Absent / empty =
         * sell nothing, which is the normal case: the tenant then has no period and runs on the floor plan.
         *
         * <p>Same relation-patch shape the detail form posts, so one DTO and one write path serve both
         * modes. A create form can only ever carry {@code Create} — an update or delete needs a period that
         * already exists.
         */
        private SubscriptionPeriodPatch periods;
    }
}
