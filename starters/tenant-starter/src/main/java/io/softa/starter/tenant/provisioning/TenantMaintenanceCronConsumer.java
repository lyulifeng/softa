package io.softa.starter.tenant.provisioning;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnClass;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.starter.cron.message.dto.CronTaskMessage;
import io.softa.starter.tenant.entitlement.SubscriptionProjectionJob;

/**
 * tenant-starter's own thin cron consumer for the crons whose domain <b>is tenant-starter itself</b> —
 * currently subscription-lifecycle expiry. Subscribes to the shared
 * {@code cron-task} broadcast under an independent subscription (Pulsar fan-out — coexists with any other
 * module's cron consumer) and handles only these; any other cron name is ignored.
 *
 * <p>A billing-domain cron whose job logic already lives in tenant-starter ({@link SubscriptionProjectionJob}).
 * Keeping its trigger here — rather than as a corehr {@code CronTaskHandler} bridge — means softa's own
 * tenant/billing crons don't live in the HR business module. The job is idempotent, so the Shared
 * subscription redelivering / multiple app instances receiving are harmless. Gated by
 * {@code mq.topics.cron-task.topic}.
 */
@Slf4j
@Component
// Optional cron-starter integration: only wires up when cron-starter is on the classpath (CronTaskMessage
// present) AND the cron-task topic is configured. A deployment using a different scheduler (Quartz,
// @Scheduled, XXL-Job, …) simply omits cron-starter — this consumer stays dormant and the app drives
// SubscriptionProjectionJob.syncDueTransitions() itself.
@ConditionalOnClass(name = "io.softa.starter.cron.message.dto.CronTaskMessage")
@ConditionalOnProperty(name = "mq.topics.cron-task.topic")
public class TenantMaintenanceCronConsumer {

    // Both match `sys_cron.name` — that is what CronScheduler puts on the message, not the seed file's `id`
    // (which is the pre-data key).
    //
    // ⚠️ Renaming either is not a code-only change. The running `SubscriptionExpiry` row was inserted by
    // business SQL and has no `sys_pre_data` binding, so the seed cannot reconcile it: a rename leaves that
    // row behind — still active, still firing — while its messages stop matching anything here, and the seed
    // adds a second row alongside it. Delete the orphan in the same deployment, or don't rename.
    /** Subscription projection + expiry reminders — seeded in tenant-starter's SysCron.TenantMaintenance.json. */
    static final String SUBSCRIPTION_EXPIRY = "SubscriptionExpiry";

    private final SubscriptionProjectionJob subscriptionProjectionJob;

    public TenantMaintenanceCronConsumer(SubscriptionProjectionJob subscriptionProjectionJob) {
        this.subscriptionProjectionJob = subscriptionProjectionJob;
    }

    @PulsarListener(topics = "${mq.topics.cron-task.topic}",
            subscriptionName = "${mq.topics.cron-task.tenant-sub:cron-task-tenant-sub}",
            subscriptionType = SubscriptionType.Shared)
    public void onMessage(CronTaskMessage message) {
        if (message == null || message.getCronName() == null) {
            return;
        }
        // Restore the context the scheduler shipped (CrossTenant crons carry crossTenant=true) so the jobs'
        // system-context sweeps run under the right ambient context.
        Context ctx = message.getContext();
        Runnable task = () -> dispatch(message.getCronName());
        if (ctx != null) {
            ContextHolder.runWith(ctx, task);
        } else {
            task.run();
        }
    }

    private void dispatch(String cronName) {
        try {
            switch (cronName) {
                case SUBSCRIPTION_EXPIRY -> {
                    int changed = subscriptionProjectionJob.syncDueTransitions();
                    log.info("[CRON] {} — {} subscription(s) transitioned (activate / expire)",
                            SUBSCRIPTION_EXPIRY, changed);
                }
                default -> { /* another module's cron on the shared topic — not ours, ignore */ }
            }
        } catch (Exception e) {
            // Swallow: rethrowing on a Shared subscription would cause a tight NACK-redelivery loop. Log for
            // alerting; the next tick retries naturally. Both jobs are idempotent.
            log.error("[CRON_FAILURE] {} — sweep failed: {}", cronName, e.getMessage(), e);
        }
    }
}
