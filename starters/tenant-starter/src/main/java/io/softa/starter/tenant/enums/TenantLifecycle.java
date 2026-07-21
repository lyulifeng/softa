package io.softa.starter.tenant.enums;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

import io.softa.framework.base.annotation.OptionSet;

/**
 * Version / subscription lifecycle stage (the "version axis" of a tenant, orthogonal to
 * {@link TenantStatus} which is the operational axis). Ops-managed; no automatic date-driven
 * transitions (avoids stored-vs-date drift). Entitlement treats TRIAL / SUBSCRIBED /
 * GRACE_PERIOD as active (grants the tenant's {@code planId} modules) and EXPIRED as degraded
 * (falls back to Free). Tenant closure/offboarding is {@link TenantStatus}'s job, not this.
 */
@Getter
@AllArgsConstructor
@OptionSet
public enum TenantLifecycle {
    SCHEDULED("Scheduled"),
    TRIAL("Trial"),
    SUBSCRIBED("Subscribed"),
    GRACE_PERIOD("GracePeriod"),
    EXPIRED("Expired"),
    ;

    @JsonValue
    private final String stage;

    /**
     * States a client / ops user may assign directly at tenant creation. {@code SCHEDULED} /
     * {@code GRACE_PERIOD} / {@code EXPIRED} are reached only via the subscription lifecycle job
     * (activate at {@code effectiveFrom} / expire at {@code effectiveTo}) or a lapse, never set by hand.
     */
    public boolean isManuallyAssignable() {
        return this == TRIAL || this == SUBSCRIBED;
    }

    /**
     * Active states that grant the subscription's plan modules. {@code SCHEDULED} (not yet effective)
     * and {@code EXPIRED} (lapsed) both degrade to the fallback plan — the resolver gates on this.
     */
    public boolean isEntitlementActive() {
        return this == TRIAL || this == SUBSCRIBED || this == GRACE_PERIOD;
    }

}
