package io.softa.starter.tenant.enums;

import lombok.Getter;
import com.fasterxml.jackson.annotation.JsonValue;

import io.softa.framework.base.annotation.OptionItem;
import io.softa.framework.base.annotation.OptionSet;
import io.softa.framework.base.enums.OptionItemTone;

/**
 * A tenant's subscription standing, projected onto its {@code TenantSubscription} row from the period
 * detail rows as of that tenant's local today. Never written by hand — the projection refresh is the
 * only writer.
 *
 * <h3>Names are deliberately neutral</h3>
 * The displayed wording lives in the option-item labels (seed data), so a deployment can relabel without
 * touching code. Two naming choices matter:
 *
 * <ul>
 *   <li><b>{@code PAID}, not {@code ACTIVE}</b> — {@code TenantStatus.ACTIVE} already means "may log in"
 *       (the operational axis). The two axes are orthogonal and can contradict: a tenant can be suspended
 *       by ops while still inside a paid period.</li>
 *   <li><b>No blanket {@code FREE} / {@code AT_FLOOR}</b> — split into {@link #EXPIRED} and
 *       {@link #NEVER_SUBSCRIBED} instead. Both fall back to the floor plan and are therefore identical
 *       in permission terms, but they demand opposite follow-up: one is a lapsed customer to win back,
 *       the other is a sales lead. Merging them hides churn inside the lead pile. Avoiding the word
 *       "free" also keeps the enum honest on a deployment whose floor tier is paid (there the floor is a
 *       zero-module placeholder, so "free" would read backwards).</li>
 * </ul>
 */
@Getter
/*
 * Labels deliberately differ from item codes on three of these. The code is persisted and read by the
 * frontend, so it is fixed for good; the label is display text and should read well. Making them match would
 * let a decision taken for storage stability dictate the wording ops sees — "Paid" lands as a payment state
 * rather than a subscription one, in a set whose other members are subscription states. Where the derived
 * name is already right (SCHEDULED, EXPIRED) no label is declared at all, so a declared one always signals a
 * deliberate override.
 */
@OptionSet(description = "Projected subscription standing of a tenant as of its local today", renamedFrom = "SubscriptionStatus")
public enum SubscriptionStatus {

    /** A paid period covers the projection date. */
    @OptionItem(label = "Subscribed", itemTone = OptionItemTone.INFO, sequence = 1)
    PAID("Paid"),

    /** A trial period covers the projection date. */
    @OptionItem(label = "On trial", itemTone = OptionItemTone.WARNING, sequence = 2)
    TRIAL("Trial"),

    /**
     * No period covers the projection date, but a later one exists. The tenant runs on the floor plan
     * until it starts — scheduling is not early activation, and the UI must say so.
     */
    @OptionItem(itemTone = OptionItemTone.NEUTRAL, sequence = 3)
    SCHEDULED("Scheduled"),

    /**
     * Periods exist but none covers the projection date and none is upcoming — the tenant bought before
     * and lapsed. Distinguished from {@link #NEVER_SUBSCRIBED} purely by "are there any period rows",
     * which costs nothing extra to determine.
     */
    @OptionItem(itemTone = OptionItemTone.NEUTRAL, sequence = 4)
    EXPIRED("Expired"),

    /**
     * No period rows at all. Note that deleting a tenant's only period puts it back here rather than in
     * {@link #EXPIRED} — deleting a period means "this record should not exist" (mis-entry), not "the
     * customer lapsed". Expiry happens by letting a period end, not by deleting it.
     */
    @OptionItem(label = "Not subscribed", itemTone = OptionItemTone.NEUTRAL, sequence = 5)
    NEVER_SUBSCRIBED("NeverSubscribed");

    @JsonValue
    private final String code;

    SubscriptionStatus(String code) {
        this.code = code;
    }
}
