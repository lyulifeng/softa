package io.softa.starter.tenant.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.utils.JsonUtils;

import static org.junit.jupiter.api.Assertions.*;

/**
 * How the tenant update endpoint splits its request body.
 *
 * <p>Binds real JSON rather than poking at a helper, because the split <i>is</i> the Jackson binding: the
 * typed {@code subscriptionId} claims that property, and {@code @JsonAnySetter} sweeps up the rest.
 *
 * <p>An earlier version took a {@code Map} and converted the nested object through
 * {@code JsonMapper.treeToValue}, which died in the running app with {@code NoSuchMethodError} — the
 * deployed softa-base and the deployed Jackson disagree on that signature. No unit test would have caught
 * it, so the fix was to stop depending on it; these cases pin the replacement.
 */
class TenantInfoControllerPayloadTest {

    @Test
    @DisplayName("periods bind to the typed field and never leak into the generic row")
    void splitsPeriodsFromTheTenantColumns() {
        // Two hazards, one split. Left in the row, the periods patch reaches the framework's nested-relation
        // pipeline, which writes through the generic model service and runs none of the period guards. And
        // the rest of the nested object must not survive either: every column on it is projected, with the
        // refresh logic as its only legitimate writer.
        TenantInfoController.TenantUpdateRequest request = JsonUtils.stringToObject("""
                {
                  "id": "1",
                  "name": "Renamed",
                  "subscriptionId": {
                    "subscriptionStatus": "Paid",
                    "periods": {
                      "Create": [{"planId": "pro", "periodType": "Trial",
                                  "effectiveStartDate": "2026-08-01", "effectiveEndDate": "2026-08-14"}],
                      "Delete": [55]
                    }
                  }
                }
                """, TenantInfoController.TenantUpdateRequest.class);

        assertNotNull(request);
        assertEquals("pro", request.periodPatch().getCreate().getFirst().getPlanId());
        assertEquals(55L, request.periodPatch().getDelete().getFirst());

        assertFalse(request.getRow().containsKey("subscriptionId"),
                "the projected subscription object must not reach the generic update");
        assertEquals("Renamed", request.getRow().get("name"), "the tenant's own edits must survive");
        assertEquals("1", request.getRow().get("id"));
    }

    @Test
    @DisplayName("an update carrying no subscription yields no patch")
    void noSubscription_noPatch() {
        TenantInfoController.TenantUpdateRequest request = JsonUtils.stringToObject(
                "{\"id\": \"1\", \"name\": \"Renamed\"}", TenantInfoController.TenantUpdateRequest.class);

        assertNull(request.periodPatch());
        assertEquals("Renamed", request.getRow().get("name"));
    }

    @Test
    @DisplayName("a subscription object with no periods yields no patch, and is still dropped")
    void projectedObjectDroppedEvenWithoutPeriods() {
        TenantInfoController.TenantUpdateRequest request = JsonUtils.stringToObject(
                "{\"id\": \"1\", \"subscriptionId\": {\"subscriptionStatus\": \"Paid\", \"planId\": \"pro\"}}",
                TenantInfoController.TenantUpdateRequest.class);

        assertNull(request.periodPatch());
        assertFalse(request.getRow().containsKey("subscriptionId"),
                "a payload naming projected columns must never reach the generic update");
    }

    @Test
    @DisplayName("the row stays mutable — formatMapId rewrites ids in place")
    void rowIsMutable() {
        TenantInfoController.TenantUpdateRequest request = JsonUtils.stringToObject(
                "{\"id\": \"1\"}", TenantInfoController.TenantUpdateRequest.class);

        assertDoesNotThrow(() -> request.getRow().put("id", 1L));
    }
}
