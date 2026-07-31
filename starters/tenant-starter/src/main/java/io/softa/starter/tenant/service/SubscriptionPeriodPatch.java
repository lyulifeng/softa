package io.softa.starter.tenant.service;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Data;

import io.softa.starter.tenant.enums.SubscriptionPeriodType;

/**
 * The subscription-period edits carried by a tenant create or update request.
 *
 * <p>Shaped as the UI's relation patch — capitalized operation keys, straight from {@code PatchType}'s
 * {@code @JsonValue} — because that is what a form posts for a {@code ONE_TO_MANY} field. Modelling it
 * explicitly rather than letting the framework's nested-relation pipeline consume it is the whole point:
 * that pipeline writes through the generic {@code ModelService}, which runs none of the period write guards
 * and does not refresh the projection. {@link TenantSubscriptionPeriodService#applyPatch} replays the same
 * operations through the guarded service instead, so one form shape serves both modes without a hole.
 *
 * <p>A missing list means "no operation of that kind", not "delete everything" — a half-filled patch must
 * never be read as a full replacement.
 */
@Data
@JsonIgnoreProperties(ignoreUnknown = true)
public class SubscriptionPeriodPatch {

    @JsonProperty("Create")
    private List<PeriodInput> create;

    @JsonProperty("Update")
    private List<PeriodInput> update;

    /** Ids only — the row is going away, so nothing else about it matters. */
    @JsonProperty("Delete")
    private List<Long> delete;

    /** One period's editable fields. On update, {@code id} identifies the row and the rest is the patch. */
    @Data
    @JsonIgnoreProperties(ignoreUnknown = true)
    public static class PeriodInput {
        private Long id;
        /**
         * Plan code (= plan.id). Must not be the floor plan — recording a floor period would say the same
         * thing as recording nothing, so the write guard rejects it.
         */
        private String planId;

        /**
         * Accept the plan either as the bare code or as the expanded reference the UI holds.
         *
         * <p>A relation field's value is a {@code ModelReference} once it has been read back from the server
         * ({@code {id, displayName}}), and the bare id while it is still being picked. Both shapes reach this
         * DTO depending on whether the row was loaded or just typed, and binding an object onto a
         * {@code String} field yields nothing — the plan arrives blank and the period is rejected as
         * incomplete, or worse, was silently dropped before that skip was removed.
         */
        @SuppressWarnings("unchecked")
        @JsonProperty("planId")
        public void setPlanId(Object value) {
            this.planId = switch (value) {
                case null -> null;
                case String code -> code;
                case Map<?, ?> reference -> {
                    Object id = ((Map<String, Object>) reference).get("id");
                    yield id == null ? null : String.valueOf(id);
                }
                default -> String.valueOf(value);
            };
        }
        /** Trial or paid; paid when null on create. */
        private SubscriptionPeriodType periodType;
        /** First day of the period; the tenant's local today when null on create. */
        private LocalDate effectiveStartDate;
        /**
         * Last day of the period; null = open-ended. Past this date the tenant falls back to the floor
         * plan — no state is stored for that, it follows from the date.
         */
        private LocalDate effectiveEndDate;
    }
}
