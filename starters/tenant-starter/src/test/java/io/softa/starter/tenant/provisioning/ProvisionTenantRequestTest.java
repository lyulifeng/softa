package io.softa.starter.tenant.provisioning;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.controller.TenantInfoController;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.SubscriptionPeriodPatch;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;
import io.softa.starter.tenant.service.impl.TenantInfoServiceImpl;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Binding tests for the tenant create payload, focused on the one failure mode that would not announce
 * itself: a period array that silently fails to bind. Provisioning treats "no periods" as a legitimate
 * request (the tenant starts on the floor plan), so a key the mapper does not recognize does not raise —
 * the plan the customer paid for just quietly never gets recorded.
 */
class ProvisionTenantRequestTest {

    /** What the create form actually posts: a relation patch keyed by the UI's capitalized operation name. */
    private static final String CREATE_PAYLOAD = """
            {
              "name": "Acme Corp",
              "code": "acme",
              "defaultTimezone": "UTC+08:00",
              "subscriptionId": {
                "periods": {
                  "Create": [
                    {
                      "planId": "pro",
                      "periodType": "Paid",
                      "effectiveStartDate": "2026-08-01",
                      "effectiveEndDate": "2027-07-31"
                    }
                  ]
                }
              }
            }
            """;

    @Test
    @DisplayName("binds the create form's relation patch, capitalized Create key included")
    void bindsRelationPatch() {
        ProvisionTenantRequest request = JsonUtils.stringToObject(CREATE_PAYLOAD, ProvisionTenantRequest.class);

        assertNotNull(request);
        assertEquals("Acme Corp", request.getName());
        assertNotNull(request.getSubscriptionId(), "the inline subscription must bind");
        assertNotNull(request.getSubscriptionId().getPeriods(), "the periods relation must bind");

        List<SubscriptionPeriodPatch.PeriodInput> periods = request.getSubscriptionId().getPeriods().getCreate();
        assertNotNull(periods, "the capitalized `Create` key must bind — otherwise the sold plan is dropped");
        assertEquals(1, periods.size());

        SubscriptionPeriodPatch.PeriodInput period = periods.getFirst();
        assertEquals("pro", period.getPlanId());
        assertEquals(SubscriptionPeriodType.PAID, period.getPeriodType());
        assertEquals(LocalDate.of(2026, 8, 1), period.getEffectiveStartDate());
        assertEquals(LocalDate.of(2027, 7, 31), period.getEffectiveEndDate());
    }

    @Test
    @DisplayName("selling several periods at once binds them all, in payload order")
    void bindsMultiplePeriods() {
        String payload = """
                {
                  "name": "Beta Ltd",
                  "subscriptionId": {
                    "periods": {
                      "Create": [
                        {"planId": "pro", "periodType": "Trial", "effectiveStartDate": "2026-08-01",
                         "effectiveEndDate": "2026-08-14"},
                        {"planId": "pro", "periodType": "Paid", "effectiveStartDate": "2026-08-15"}
                      ]
                    }
                  }
                }
                """;

        List<SubscriptionPeriodPatch.PeriodInput> periods = JsonUtils
                .stringToObject(payload, ProvisionTenantRequest.class)
                .getSubscriptionId().getPeriods().getCreate();

        assertEquals(2, periods.size());
        assertEquals(SubscriptionPeriodType.TRIAL, periods.get(0).getPeriodType());
        assertEquals(SubscriptionPeriodType.PAID, periods.get(1).getPeriodType());
        // Open-ended: the paid period that follows the trial has no end date.
        assertNull(periods.get(1).getEffectiveEndDate());
    }

    @Test
    @DisplayName("a tenant created without a subscription binds cleanly — selling nothing is the normal case")
    void bindsWithoutSubscription() {
        ProvisionTenantRequest request = JsonUtils.stringToObject(
                "{\"name\": \"Cathay Pte\", \"defaultTimezone\": \"UTC+08:00\"}", ProvisionTenantRequest.class);

        assertNotNull(request);
        assertNull(request.getSubscriptionId());
    }

    @Test
    @DisplayName("form fields provisioning does not accept are ignored rather than rejected")
    void ignoresUnknownFormFields() {
        ProvisionTenantRequest request = JsonUtils.stringToObject("""
                {"name": "Delta Inc", "status": "Suspended", "activatedTime": "2026-01-01 00:00:00",
                 "subscriptionId": {"subscriptionStatus": "Paid", "periods": {"Create": [{"planId": "pro"}]}}}
                """, ProvisionTenantRequest.class);

        assertNotNull(request);
        assertEquals("Delta Inc", request.getName());
        // The projected column came along for the ride and was dropped; the period still bound.
        assertEquals("pro", request.getSubscriptionId().getPeriods().getCreate().getFirst().getPlanId());
    }

    @Test
    @DisplayName("the plan binds whether it arrives as a bare code or as an expanded reference")
    void bindsPlanInEitherShape() {
        // A relation field holds a ModelReference once read back from the server and a bare id while being
        // picked, and both shapes reach this DTO. Binding an object onto a String field yields nothing, so
        // the plan would arrive blank and the period be rejected as incomplete — with the row looking
        // perfectly filled in on screen.
        String payload = """
                {"name": "Echo Retail", "subscriptionId": {"periods": {"Create": [
                    {"planId": "plan.pro", "periodType": "Paid", "effectiveStartDate": "2026-08-01"},
                    {"planId": {"id": "plan.enterprise", "displayName": "Enterprise"},
                     "periodType": "Paid", "effectiveStartDate": "2026-09-01"}
                ]}}}
                """;

        List<SubscriptionPeriodPatch.PeriodInput> periods = JsonUtils
                .stringToObject(payload, ProvisionTenantRequest.class)
                .getSubscriptionId().getPeriods().getCreate();

        assertEquals("plan.pro", periods.get(0).getPlanId(), "the bare code must pass through");
        assertEquals("plan.enterprise", periods.get(1).getPlanId(),
                "the reference must be reduced to its id, not dropped");
    }
}
