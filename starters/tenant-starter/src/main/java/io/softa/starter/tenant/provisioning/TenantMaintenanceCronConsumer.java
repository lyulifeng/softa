package io.softa.starter.tenant.provisioning;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.starter.cron.message.dto.CronTaskMessage;
import io.softa.starter.tenant.entitlement.SubscriptionExpiryJob;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

/**
 * tenant-starter's own thin cron consumer for the crons whose domain <b>is tenant-starter itself</b> —
 * currently the provisioning-timeout guard and subscription-lifecycle expiry. Subscribes to the shared
 * {@code cron-task} broadcast under an independent subscription (Pulsar fan-out — coexists with any other
 * module's cron consumer) and handles only these; any other cron name is ignored.
 *
 * <p>Both are tenant / billing-domain crons whose job logic already lives in tenant-starter
 * ({@link TenantProvisioningStatusService#failTimedOut()} and {@link SubscriptionExpiryJob}). Keeping their
 * trigger here — rather than as a corehr {@code CronTaskHandler} bridge — means softa's own tenant/billing
 * crons don't live in the HR business module. Both jobs are idempotent, so the Shared subscription
 * redelivering / multiple app instances receiving are harmless. Gated by {@code mq.topics.cron-task.topic}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.topics.cron-task.topic")
public class TenantMaintenanceCronConsumer {

    /** Provisioning-timeout guard — {@code sys_cron.name} in tenant-starter's SysCron.TenantMaintenance.json. */
    static final String PROVISIONING_TIMEOUT = "ProvisioningTimeout";
    /** Subscription-lifecycle expiry — {@code sys_cron.name} seeded by the app (hcm 4.cron-data.sql). */
    static final String SUBSCRIPTION_EXPIRY = "SubscriptionExpiry";

    private final TenantProvisioningStatusService statusService;
    private final SubscriptionExpiryJob subscriptionExpiryJob;

    public TenantMaintenanceCronConsumer(TenantProvisioningStatusService statusService,
                                         SubscriptionExpiryJob subscriptionExpiryJob) {
        this.statusService = statusService;
        this.subscriptionExpiryJob = subscriptionExpiryJob;
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
                case PROVISIONING_TIMEOUT -> {
                    int failed = statusService.failTimedOut();
                    if (failed > 0) {
                        log.warn("[CRON] {} — {} tenant(s) timed out in provisioning → FAILED",
                                PROVISIONING_TIMEOUT, failed);
                    }
                }
                case SUBSCRIPTION_EXPIRY -> {
                    int changed = subscriptionExpiryJob.syncDueTransitions();
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
