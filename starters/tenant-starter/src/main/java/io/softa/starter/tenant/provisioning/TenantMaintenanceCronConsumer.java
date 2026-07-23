package io.softa.starter.tenant.provisioning;

import lombok.extern.slf4j.Slf4j;
import org.apache.pulsar.client.api.SubscriptionType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.pulsar.annotation.PulsarListener;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.starter.cron.message.dto.CronTaskMessage;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

/**
 * tenant-starter's own thin cron consumer for the <b>provisioning-timeout guard</b>. Subscribes to the
 * shared {@code cron-task} broadcast under an independent subscription (Pulsar fan-out — coexists with any
 * other module's cron consumer) and handles only the crons whose domain is tenant-starter's own; currently
 * just {@code ProvisioningTimeout}.
 *
 * <p>This is deliberately NOT wired through a business-module cron dispatcher: {@link
 * TenantProvisioningStatusService#failTimedOut()} is a tenant-starter <b>internal self-heal</b> (scan tenants
 * stuck in {@code INITIALIZING} past the timeout → {@code FAILED}), so the starter carries its own listener
 * for it and stays self-sufficient — no dependency on any app/business consumer. {@code failTimedOut()} is
 * idempotent, so the Shared subscription redelivering / multiple app instances receiving are all harmless.
 *
 * <p>Gated by {@code mq.topics.cron-task.topic} (bean absent when unconfigured).
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "mq.topics.cron-task.topic")
public class TenantMaintenanceCronConsumer {

    /** Must equal the {@code sys_cron.name} in tenant-starter's data-system/SysCron.TenantMaintenance.json. */
    static final String PROVISIONING_TIMEOUT = "ProvisioningTimeout";

    private final TenantProvisioningStatusService statusService;

    public TenantMaintenanceCronConsumer(TenantProvisioningStatusService statusService) {
        this.statusService = statusService;
    }

    @PulsarListener(topics = "${mq.topics.cron-task.topic}",
            subscriptionName = "${mq.topics.cron-task.tenant-sub:cron-task-tenant-sub}",
            subscriptionType = SubscriptionType.Shared)
    public void onMessage(CronTaskMessage message) {
        if (message == null || message.getCronName() == null) {
            return;
        }
        // CrossTenant cron: the scheduler ships a context (crossTenant=true); restore it so failTimedOut's
        // system-context sweep runs under the right ambient context.
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
            if (PROVISIONING_TIMEOUT.equals(cronName)) {
                int failed = statusService.failTimedOut();
                if (failed > 0) {
                    log.warn("[CRON] {} — {} tenant(s) timed out in provisioning → FAILED",
                            PROVISIONING_TIMEOUT, failed);
                }
            }
            // Any other cronName on the shared topic belongs to another module — not ours, ignore.
        } catch (Exception e) {
            // Swallow: rethrowing on a Shared subscription would cause a tight NACK-redelivery loop. Log for
            // alerting; the next tick retries naturally. failTimedOut is idempotent, so no state is lost.
            log.error("[CRON_FAILURE] {} — provisioning-timeout sweep failed: {}", cronName, e.getMessage(), e);
        }
    }
}
