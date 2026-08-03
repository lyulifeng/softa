package io.softa.starter.tenant.service.impl;

import java.time.LocalDate;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.ModelService;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.SubscriptionStatus;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * What a projection write is allowed to contain.
 *
 * <p>This exists because of a data-loss bug worth restating. The subscription entity carries a virtual
 * {@code periods} relation so the UI can render period inputs from metadata. Writing the projection by
 * round-tripping that entity with nulls preserved put {@code periods: null} in the update map, and the
 * framework reads "relation key present, value null" as "clear the relation" — so it deleted every period of
 * that subscription. A refresh follows every period write, so each newly recorded period was inserted and
 * then deleted by the very refresh meant to describe it. No exception, no rollback, no trace.
 *
 * <p>A comment cannot prevent that coming back. These assertions can.
 */
class TenantSubscriptionProjectionWriteTest {

    private final ModelService<Long> modelService = mockModelService();
    private final TenantSubscriptionServiceImpl service = newService();

    @Test
    @DisplayName("no relation field ever reaches the update map")
    void writesNoRelationFields() {
        service.updateProjection(projection());

        Map<String, Object> row = captureRow();
        assertFalse(row.containsKey("periods"),
                "a relation key in a projection write deletes the rows it was describing");
        // Any relation would do the same damage; the assertion is about the whole class of field, so it is
        // stated as a whitelist rather than a list of names to avoid.
        assertEquals(Set.of("id", "subscriptionStatus", "planId", "periodType", "currentPeriodId",
                        "currentStartDate", "currentEndDate", "nextStartDate", "projectedForDate",
                        "projectedTime"),
                row.keySet(),
                "the projection writes exactly its own columns — nothing inherited, nothing relational");
    }

    @Test
    @DisplayName("nulls are written, not dropped")
    void writesNullsExplicitly() {
        // A lapsed tenant has to lose its plan. Omitting the key would leave the old value in place, and the
        // resolver reads that column — the tenant would keep the plan it stopped paying for.
        TenantSubscription lapsed = projection();
        lapsed.setPlanId(null);
        lapsed.setCurrentPeriodId(null);
        lapsed.setCurrentStartDate(null);

        service.updateProjection(lapsed);

        Map<String, Object> row = captureRow();
        assertTrue(row.containsKey("planId"), "the key must be present for the null to be written");
        assertNull(row.get("planId"));
        assertNull(row.get("currentPeriodId"));
        assertNull(row.get("currentStartDate"));
    }

    private static TenantSubscription projection() {
        TenantSubscription sub = new TenantSubscription();
        sub.setId(9001L);
        sub.setSubscriptionStatus(SubscriptionStatus.PAID);
        sub.setPlanId("plan.pro");
        sub.setProjectedForDate(LocalDate.of(2026, 7, 30));
        return sub;
    }

    @SuppressWarnings("unchecked")
    private Map<String, Object> captureRow() {
        ArgumentCaptor<Map<String, Object>> captor = ArgumentCaptor.forClass(Map.class);
        verify(modelService).updateOne(eq("TenantSubscription"), captor.capture());
        return captor.getValue();
    }

    @SuppressWarnings("unchecked")
    private ModelService<Long> mockModelService() {
        ModelService<Long> mocked = mock(ModelService.class);
        when(mocked.updateOne(eq("TenantSubscription"), any())).thenReturn(true);
        return mocked;
    }

    /** The collaborator is an `@Autowired` field, so it goes in by reflection. */
    private TenantSubscriptionServiceImpl newService() {
        TenantSubscriptionServiceImpl impl = new TenantSubscriptionServiceImpl();
        ReflectionTestUtils.setField(impl, "modelService", modelService);
        return impl;
    }
}
