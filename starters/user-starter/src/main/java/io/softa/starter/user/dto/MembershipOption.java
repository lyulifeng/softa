package io.softa.starter.user.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import io.softa.starter.user.enums.AccountStatus;

/**
 * One company a person belongs to, offered as a login target.
 *
 * <p>Authentication proves WHO you are ({@code UserProfile}); this says WHERE you can go. The two
 * are separate steps now because a person can belong to several companies, and the session must
 * carry one membership — {@code accountId} is what the session ends up holding.
 *
 * @param accountId  the membership to log into (this becomes the session's userId)
 * @param tenantId   the company
 * @param tenantName display name for the picker
 * @param status     shown as a badge; a non-ACTIVE option is listed but not selectable, so the
 *                   person can see that the company exists and why they cannot enter (frozen /
 *                   locked) rather than facing a list that silently omits it
 */
public record MembershipOption(Long accountId, Long tenantId, String tenantName, AccountStatus status) {

    /**
     * Whether this option can actually be entered.
     *
     * <p>{@code @JsonProperty} is required, not decoration: the name is neither a record component
     * nor a {@code getX}/{@code isX} getter, so Jackson does not discover it and the field simply
     * never reaches the client — where every option then reads as unselectable and the picker
     * refuses every company it just listed. {@code isResolved()} on AuthenticationResult is carried
     * only because that name happens to match the convention.
     */
    @JsonProperty("selectable")
    public boolean selectable() {
        return status == AccountStatus.ACTIVE;
    }
}
