package io.softa.starter.tenant.provisioning;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.starter.cron.message.dto.CronTaskMessage;
import io.softa.starter.tenant.entitlement.SubscriptionProjectionJob;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * How a cron tick is routed, and what happens when the sweep it triggers blows up.
 *
 * <p>This consumer sits on a <b>shared broadcast topic</b>: every module's cron message arrives here, so
 * "ignore what is not mine" is load-bearing rather than defensive — a fall-through that ran the wrong sweep
 * would fire tenant maintenance on some other module's schedule.
 *
 * <p>The swallow-and-log on failure is the case most worth pinning. Rethrowing on a Shared subscription NACKs
 * the message into a tight redelivery loop, which is why the {@code catch} is there; but a {@code catch} that
 * broad is also exactly what quietly hid a nightly job failing for months in this codebase, so the behaviour
 * has to be a deliberate, tested choice rather than an accident.
 */
class TenantMaintenanceCronConsumerTest {

    private SubscriptionProjectionJob projectionJob;
    private TenantMaintenanceCronConsumer consumer;

    @BeforeEach
    void setUp() {
        projectionJob = mock(SubscriptionProjectionJob.class);
        consumer = new TenantMaintenanceCronConsumer(projectionJob);
    }

    // ─── routing ───


    @Test
    @DisplayName("the subscription-expiry cron runs only the projection sweep")
    void subscriptionExpiry_runsOnlyTheSweep() {
        consumer.onMessage(message(TenantMaintenanceCronConsumer.SUBSCRIPTION_EXPIRY));

        verify(projectionJob).syncDueTransitions();
    }

    @Test
    @DisplayName("another module's cron on the shared topic is ignored, not fallen through to")
    void foreignCronName_ignored() {
        // The topic is a broadcast; corehr, flow and user crons all land here. A default branch that did
        // anything would run tenant maintenance on somebody else's schedule.
        consumer.onMessage(message("DynamicRoleSync"));
        consumer.onMessage(message("PayrollPeriodClose"));

        verifyNoInteractions(projectionJob);
    }

    @Test
    @DisplayName("a message with no cron name is dropped rather than routed")
    void missingCronName_dropped() {
        consumer.onMessage(message(null));
        consumer.onMessage(null);

        verifyNoInteractions(projectionJob);
    }

    // ─── the context the scheduler shipped ───

    @Test
    @DisplayName("the shipped context is restored around the sweep")
    void shippedContext_restoredForTheSweep() {
        // CrossTenant crons carry crossTenant=true. Without restoring it, the sweep's own system context is
        // entered from whatever ambient context the Pulsar listener thread happens to hold.
        Context shipped = new Context();
        shipped.setCrossTenant(true);
        CronTaskMessage message = message(TenantMaintenanceCronConsumer.SUBSCRIPTION_EXPIRY);
        message.setContext(shipped);

        when(projectionJob.syncDueTransitions()).thenAnswer(invocation -> {
            assertThat(ContextHolder.getContext())
                    .as("the sweep must see the context the scheduler sent")
                    .isSameAs(shipped);
            return 0;
        });

        consumer.onMessage(message);

        verify(projectionJob).syncDueTransitions();
    }

    @Test
    @DisplayName("no context on the message — the sweep still runs")
    void noContext_stillRuns() {
        // An older scheduler, or a hand-published message, may carry none. Refusing to run would silently
        // stop tenant maintenance instead of degrading to the ambient context.
        consumer.onMessage(message(TenantMaintenanceCronConsumer.SUBSCRIPTION_EXPIRY));

        verify(projectionJob).syncDueTransitions();
    }

    // ─── failure ───

    @Test
    @DisplayName("a failing sweep is swallowed — a Shared subscription must not NACK-loop")
    void failingSweep_swallowed() {
        // Rethrowing here redelivers immediately and forever, since the next attempt fails the same way. The
        // next scheduled tick is the retry; both sweeps are idempotent.
        when(projectionJob.syncDueTransitions()).thenThrow(new IllegalStateException("db down"));

        assertThatCode(() -> consumer.onMessage(message(TenantMaintenanceCronConsumer.SUBSCRIPTION_EXPIRY)))
                .doesNotThrowAnyException();
    }


    /**
     * The names are matched against {@code sys_cron.name}, which is what {@code CronScheduler} puts on the
     * message — not the seed file's {@code id}. Referencing the constants rather than retyping the strings is
     * deliberate: a test with its own copies would still pass after a rename that stopped matching the
     * running rows.
     */
    @Test
    @DisplayName("the matched names are the sys_cron names, spelled as the seed spells them")
    void cronNamesMatchTheSeed() {
        assertThat(TenantMaintenanceCronConsumer.SUBSCRIPTION_EXPIRY).isEqualTo("SubscriptionExpiry");
    }

    private static CronTaskMessage message(String cronName) {
        CronTaskMessage message = new CronTaskMessage();
        message.setCronName(cronName);
        return message;
    }
}
