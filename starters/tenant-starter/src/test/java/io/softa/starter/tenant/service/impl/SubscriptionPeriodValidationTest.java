package io.softa.starter.tenant.service.impl;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.Plan;
import io.softa.starter.tenant.entity.TenantSubscriptionPeriod;
import io.softa.starter.tenant.enums.SubscriptionPeriodType;
import io.softa.starter.tenant.service.SubscriptionProjectionService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyMap;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * The guards on the period table — the only thing standing between ops and a subscription history that
 * cannot be interpreted.
 *
 * <p>No database constraint can express any of them. Overlap in particular has no unique index that could
 * catch it (two rows over the same dates are two perfectly legal rows), and the projection downstream has to
 * pick <i>one</i> period as today's: given an overlap it applies a deterministic tie-break and serves
 * whichever plan wins, silently. That tie-break exists as a last resort for data that should never have been
 * written, so this guard is what keeps it unreachable.
 *
 * <p>The accept cases matter as much as the reject cases: adjacency is the ordinary renewal and a gap is a
 * legitimate sale, so a guard that over-rejects breaks the two things ops does most.
 */
class SubscriptionPeriodValidationTest {

    private static final long SUB_ID = 9001L;
    private static final LocalDate START = LocalDate.of(2026, 8, 1);

    private static final Plan FREE = plan("plan.free", 0);
    private static final Plan PRO = plan("plan.pro", 10);

    private TenantSubscriptionPeriodServiceImpl service;
    /** What a catalog-wide scan sees — {@code floorPlan()} picks the lowest tier out of this. */
    private List<Plan> catalog;
    /** What a by-id lookup resolves to — {@code planById()}, for the trial tier check. */
    private Plan lookedUpPlan;
    /** The subscription's already-stored periods, scanned for overlap. */
    private List<TenantSubscriptionPeriod> stored;

    @BeforeEach
    void setUp() {
        catalog = new ArrayList<>(List.of(FREE, PRO));
        lookedUpPlan = PRO;
        stored = new ArrayList<>();
        service = newService();
    }

    // ─── required fields ───

    @Test
    @DisplayName("a period with no subscription belongs to nobody")
    void subscriptionRequired() {
        assertThatThrownBy(() -> service.createOne(period(null, START, START.plusMonths(12), "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("must belong to a subscription");
    }

    @Test
    @DisplayName("a period with no start date cannot be placed on the timeline")
    void startDateRequired() {
        assertThatThrownBy(() -> service.createOne(period(SUB_ID, null, START.plusMonths(12), "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("start date is required");
    }

    @Test
    @DisplayName("a blank plan is rejected rather than stored as a period selling nothing")
    void planRequired() {
        // Reached, not skipped: `applyPatch` deliberately forwards blank-plan rows so this message is what
        // the user sees. A row dropped earlier looked like a successful save with the period missing.
        assertThatThrownBy(() -> service.createOne(period(SUB_ID, START, START.plusMonths(12), "   ")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("plan is required");
    }

    @Test
    @DisplayName("a period with no type cannot be told apart from a trial")
    void periodTypeRequired() {
        TenantSubscriptionPeriod period = period(SUB_ID, START, START.plusMonths(12), "plan.pro");
        period.setPeriodType(null);

        assertThatThrownBy(() -> service.createOne(period))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Period type");
    }

    // ─── the date bounds ───

    @Test
    @DisplayName("an end date before the start date is rejected")
    void endBeforeStart_rejected() {
        // The form validates this too, but the form is not the boundary — this endpoint is. A backwards
        // period is worse than an empty one: it covers no day at all, so the projection reports the tenant as
        // never subscribed while ops is looking at a row that says otherwise.
        assertThatThrownBy(() -> service.createOne(period(SUB_ID, START, START.minusDays(1), "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("cannot precede its start date");
    }

    @Test
    @DisplayName("a single-day period — start equals end — is allowed")
    void sameDayStartAndEnd_allowed() {
        // Both bounds are inclusive, so this is one covered day, not zero. Rejecting it would make a one-day
        // trial unexpressible.
        assertThatCode(() -> service.createOne(period(SUB_ID, START, START, "plan.pro")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an open-ended period needs no end date")
    void openEndedPeriod_allowed() {
        assertThatCode(() -> service.createOne(period(SUB_ID, START, null, "plan.pro")))
                .doesNotThrowAnyException();
    }

    // ─── the floor plan is not sellable ───

    @Test
    @DisplayName("the floor plan cannot be recorded as a period")
    void floorPlanPeriod_rejected() {
        // "On the floor plan" and "no period at all" are the same state. Two representations of one state
        // means the projection cannot distinguish a customer who lapsed from one who was never sold
        // anything — which is exactly the EXPIRED / NEVER_SUBSCRIBED distinction ops reads the list for.
        assertThatThrownBy(() -> service.createOne(period(SUB_ID, START, START.plusMonths(12), FREE.getId())))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("floor plan cannot be sold");
    }

    @Test
    @DisplayName("with no plan catalog seeded, the floor guard stands down rather than rejecting everything")
    void noCatalog_floorGuardSkipped() {
        // `floorPlan()` returns null before the catalog is seeded. Treating that as "everything is the floor"
        // would make a fresh install unable to record any period at all.
        catalog = List.of();

        assertThatCode(() -> service.createOne(period(SUB_ID, START, START.plusMonths(12), "plan.pro")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("only a plan above the floor can be trialled")
    void trialOnNonUpgradePlan_rejected() {
        // A trial's whole purpose is temporary access to something the tenant does not have. A trial of a
        // plan no better than the floor grants nothing and then "expires", which reads to the customer as
        // access being taken away.
        Plan legacy = plan("plan.legacy", null);   // no tier — treated as floor level
        catalog = List.of(FREE, PRO, legacy);
        lookedUpPlan = legacy;

        TenantSubscriptionPeriod period = period(SUB_ID, START, START.plusMonths(1), legacy.getId());
        period.setPeriodType(SubscriptionPeriodType.TRIAL);

        assertThatThrownBy(() -> service.createOne(period))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("above the floor can be trialled");
    }

    @Test
    @DisplayName("a paid period on that same plan is fine — the tier rule is trial-only")
    void paidOnNonUpgradePlan_allowed() {
        // Selling a low-tier plan is a pricing decision, not a data-integrity problem; only the trial case is
        // incoherent. Pinned so the guard cannot drift into blocking paid sales.
        Plan legacy = plan("plan.legacy", null);
        catalog = List.of(FREE, PRO, legacy);
        lookedUpPlan = legacy;

        assertThatCode(() -> service.createOne(period(SUB_ID, START, START.plusMonths(1), legacy.getId())))
                .doesNotThrowAnyException();
    }

    // ─── overlap ───

    @Test
    @DisplayName("a period landing inside an existing one is rejected")
    void containedOverlap_rejected() {
        stored.add(storedPeriod(1L, START, START.plusMonths(12)));

        assertThatThrownBy(() -> service.createOne(
                period(SUB_ID, START.plusMonths(3), START.plusMonths(6), "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps an existing one");
    }

    @Test
    @DisplayName("a period straddling an existing one's end is rejected")
    void tailOverlap_rejected() {
        stored.add(storedPeriod(1L, START, START.plusMonths(12)));

        assertThatThrownBy(() -> service.createOne(
                period(SUB_ID, START.plusMonths(11), START.plusMonths(18), "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps an existing one");
    }

    @Test
    @DisplayName("an open-ended existing period blocks everything after its start")
    void openEndedIncumbent_blocksLater() {
        // A null end date means "until further notice", so nothing can be scheduled after it — the incumbent
        // has to be given an end date first. Getting this backwards is the subtle case: a null end read as
        // "ends immediately" would let a second period silently shadow the live one.
        stored.add(storedPeriod(1L, START, null));

        assertThatThrownBy(() -> service.createOne(period(SUB_ID, START.plusYears(5), null, "plan.pro")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("overlaps an existing one");
    }

    @Test
    @DisplayName("adjacent periods — the next starts the day after — are not an overlap")
    void adjacentPeriods_allowed() {
        // The ordinary renewal, and the whole reason the bound is `isBefore` rather than `!isAfter`: an
        // off-by-one here rejects every seamless renewal ops enters.
        stored.add(storedPeriod(1L, START, START.plusMonths(12)));

        assertThatCode(() -> service.createOne(
                period(SUB_ID, START.plusMonths(12).plusDays(1), START.plusMonths(24), "plan.pro")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("a gap between periods is allowed — the customer simply did not buy those months")
    void gapBetweenPeriods_allowed() {
        stored.add(storedPeriod(1L, START, START.plusMonths(12)));

        assertThatCode(() -> service.createOne(
                period(SUB_ID, START.plusMonths(18), START.plusMonths(30), "plan.pro")))
                .doesNotThrowAnyException();
    }

    // ─── fixtures ───

    /**
     * Both collaborators are injected fields, so they go in by reflection (as in
     * {@code TenantSubscriptionProjectionWriteTest}).
     *
     * <p>Two of them are called {@code modelService}: the subclass's own, and the inherited one
     * {@code EntityServiceImpl} writes through. The inherited one is named explicitly by its declaring class —
     * the subclass field shadows it for the 3-argument setter, and leaving it unset makes every accept case
     * NPE on the write, which would let a guard that wrongly rejects pass as "threw something".
     */
    @SuppressWarnings("unchecked")
    private TenantSubscriptionPeriodServiceImpl newService() {
        TenantSubscriptionPeriodServiceImpl impl = spy(new TenantSubscriptionPeriodServiceImpl());
        ModelService<Long> modelService = mock(ModelService.class);

        // One mocked signature serves both plan reads, so they are told apart the way the call sites differ:
        // `floorPlan()` scans the catalog with no filter, `planById()` asks for one id.
        when(modelService.searchList(eq("Plan"), any(FlexQuery.class), eq(Plan.class)))
                .thenAnswer(invocation -> {
                    FlexQuery query = invocation.getArgument(1);
                    return Filters.isEmpty(query.getFilters())
                            ? catalog
                            : (lookedUpPlan == null ? List.of() : List.of(lookedUpPlan));
                });
        // No tenant owns this subscription in these fixtures, so `stampOwnerTenant` leaves tenantId null and
        // `refreshOwner` logs that it cannot refresh — neither is under test here.
        when(modelService.searchList(eq("TenantInfo"), any(FlexQuery.class), any(Class.class)))
                .thenReturn(List.of());
        when(modelService.createOne(anyString(), anyMap())).thenReturn(4242L);

        ReflectionTestUtils.setField(impl, "modelService", modelService);
        ReflectionTestUtils.setField(impl, EntityServiceImpl.class, "modelService", modelService,
                ModelService.class);
        ReflectionTestUtils.setField(impl, "projectionService", mock(SubscriptionProjectionService.class));
        // The overlap scan reads the stored rows; the list is mutated per test, so the stub returns it live.
        doReturn(stored).when(impl).searchList(any(FlexQuery.class));
        return impl;
    }

    private static TenantSubscriptionPeriod period(Long subscriptionId, LocalDate from, LocalDate to,
                                                   String planId) {
        TenantSubscriptionPeriod period = new TenantSubscriptionPeriod();
        period.setSubscriptionId(subscriptionId);
        period.setEffectiveStartDate(from);
        period.setEffectiveEndDate(to);
        period.setPlanId(planId);
        period.setPeriodType(SubscriptionPeriodType.PAID);
        return period;
    }

    private static TenantSubscriptionPeriod storedPeriod(Long id, LocalDate from, LocalDate to) {
        TenantSubscriptionPeriod period = period(SUB_ID, from, to, "plan.pro");
        period.setId(id);
        return period;
    }

    private static Plan plan(String id, Integer tier) {
        Plan plan = new Plan();
        plan.setId(id);
        plan.setTier(tier);
        return plan;
    }
}
