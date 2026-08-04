package io.softa.starter.tenant.service.impl;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.InOrder;

import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.SubscriptionPeriodPatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * The order {@code applyPatch} replays a relation patch in.
 *
 * <p>Worth pinning because it is the difference between a legitimate edit succeeding and being rejected:
 * replacing a period means deleting one row and creating another over the same dates, and if create ran
 * first the overlap guard would compare the new row against the row on its way out.
 */
class SubscriptionPeriodPatchOrderTest {

    @Test
    @DisplayName("deletes free their interval before creates claim it")
    void deleteThenUpdateThenCreate() {
        TenantSubscriptionPeriodServiceImpl service = newService();
        // The three write entry points are the units under observation, not the units under test — each is
        // separately covered for its guards.
        doReturn(true).when(service).deleteByIds(anyList());
        doReturn(true).when(service).updateOne(any(TenantSubscriptionPeriod.class));
        doReturn(1L).when(service).createOne(any(TenantSubscriptionPeriod.class));

        SubscriptionPeriodPatch patch = new SubscriptionPeriodPatch();
        patch.setDelete(List.of(11L));
        patch.setUpdate(List.of(input(12L, "plan.pro")));
        patch.setCreate(List.of(input(null, "plan.enterprise")));

        service.applyPatch(77L, patch);

        InOrder order = inOrder(service);
        order.verify(service).deleteByIds(List.of(11L));
        order.verify(service).updateOne(any(TenantSubscriptionPeriod.class));
        order.verify(service).createOne(any(TenantSubscriptionPeriod.class));
    }

    @Test
    @DisplayName("a row with no plan reaches the guard instead of being dropped")
    void blankPlanRowIsNotSwallowed() {
        // The previous behaviour skipped it, on the theory that a blank row was one the form had left
        // behind. But a plan that fails to bind also arrives blank, so filled-in rows were silently
        // discarded — the save succeeded and the period simply was not there. Letting it through means
        // `validate`'s "Period plan is required" reaches the user.
        TenantSubscriptionPeriodServiceImpl service = newService();
        doReturn(1L).when(service).createOne(any(TenantSubscriptionPeriod.class));

        SubscriptionPeriodPatch patch = new SubscriptionPeriodPatch();
        patch.setCreate(List.of(input(null, "  ")));

        service.applyPatch(77L, patch);

        verify(service).createOne(any(TenantSubscriptionPeriod.class));
    }

    @Test
    @DisplayName("a delete whose row is already gone still leaves the projection refreshed")
    void deleteOfMissingRow_stillRefreshes() {
        // `deleteByIds` derives the owner by loading each row, so an id that no longer exists yields no
        // owner and its own refresh is skipped. The projection would then keep describing periods that are
        // gone — and authorization reads the projection.
        TenantSubscriptionPeriodServiceImpl service = newService();
        doReturn(true).when(service).deleteByIds(anyList());

        SubscriptionPeriodPatch patch = new SubscriptionPeriodPatch();
        patch.setDelete(List.of(404L));
        service.applyPatch(77L, patch);

        // The patch-level refresh uses the subscription id it was handed, which cannot go missing.
        verify(service).refreshOwner(77L);
    }

    @Test
    @DisplayName("a plan arriving as a reference object binds to its id")
    void planAsReferenceObject_bindsToId() {
        // What the UI actually sends. A ManyToOne field posts `{id, displayName}`, not a bare code — a setter
        // typed to String would leave the plan null, and the row would then be rejected as "plan is required"
        // even though the user had picked one. Accepted here rather than reshaped on the client because the
        // reference shape is the framework's own convention for every relation field.
        SubscriptionPeriodPatch.PeriodInput row = new SubscriptionPeriodPatch.PeriodInput();
        row.setPlanId(Map.of("id", "plan.pro", "displayName", "Pro"));

        assertEquals("plan.pro", row.getPlanId());
    }

    @Test
    @DisplayName("a reference object with no id binds to null, not to the literal map")
    void planReferenceWithoutId_bindsNull() {
        // An empty picker posts `{}`. Stringifying that would store "{}" as the plan id — a value that looks
        // filled in, passes the not-blank guard, and matches no plan in the catalog.
        SubscriptionPeriodPatch.PeriodInput row = new SubscriptionPeriodPatch.PeriodInput();
        row.setPlanId(Map.of());

        assertNull(row.getPlanId());
    }

    @Test
    @DisplayName("a bare code still binds — the typed API and the UI payload share one setter")
    void planAsBareCode_binds() {
        SubscriptionPeriodPatch.PeriodInput row = new SubscriptionPeriodPatch.PeriodInput();
        row.setPlanId("plan.enterprise");

        assertEquals("plan.enterprise", row.getPlanId());
    }

    @Test
    @DisplayName("a patch with no operations touches nothing")
    void emptyPatchIsANoOp() {
        TenantSubscriptionPeriodServiceImpl service = newService();

        service.applyPatch(77L, new SubscriptionPeriodPatch());
        service.applyPatch(77L, null);

        // A half-filled patch must never be read as "replace everything".
        verify(service, never()).deleteByIds(anyList());
        verify(service, never()).updateOne(any(TenantSubscriptionPeriod.class));
        verify(service, never()).createOne(any(TenantSubscriptionPeriod.class));
    }

    /**
     * The collaborators are `@Autowired` fields, so an unwired instance NPEs the moment a write path tries
     * to refresh. Stubbing that one seam keeps these cases about the patch's own logic — the refresh itself
     * is covered by the projection service's tests.
     */
    private static TenantSubscriptionPeriodServiceImpl newService() {
        TenantSubscriptionPeriodServiceImpl service = spy(new TenantSubscriptionPeriodServiceImpl());
        doNothing().when(service).refreshOwner(anyLong());
        return service;
    }

    private static SubscriptionPeriodPatch.PeriodInput input(Long id, String planId) {
        SubscriptionPeriodPatch.PeriodInput row = new SubscriptionPeriodPatch.PeriodInput();
        row.setId(id);
        row.setPlanId(planId);
        row.setPeriodType(SubscriptionPeriodType.PAID);
        // Set so the create branch never needs the tenant's timezone, which would pull in the projection
        // service this test deliberately does not wire.
        row.setEffectiveStartDate(java.time.LocalDate.of(2026, 8, 1));
        return row;
    }
}
