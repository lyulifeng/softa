package io.softa.starter.tenant.entitlement;

import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.message.EntitlementChangeMessage;

/**
 * Immediate entitlement invalidation — on {@link TenantEntitlementChangedEvent}, after the
 * mutating transaction commits: (1) evict the tenant's {@code entl:} cache in-process, then
 * (2) fan the fresh module set out to MQ so the user side can clean up over-plan role grants
 * (no-op if MQ unconfigured). {@code AFTER_COMMIT} so a rolled-back change never invalidates;
 * {@code fallbackExecution} so it still runs outside a transaction. Kept in tenant-starter
 * (not user-starter) so entitlement stays self-contained when user-starter isn't installed.
 */
@Slf4j
@Component
public class EntitlementChangedListener {

    private final EntitlementResolver resolver;
    private final EntitlementChangeProducer producer;

    public EntitlementChangedListener(EntitlementResolver resolver, EntitlementChangeProducer producer) {
        this.resolver = resolver;
        this.producer = producer;
    }

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onEntitlementChanged(TenantEntitlementChangedEvent event) {
        if (event == null || event.tenantId() == null) {
            return;
        }
        Long tenantId = event.tenantId();
        resolver.evict(tenantId);   // drop the stale entl: snapshot
        // resolve() recomputes off the just-evicted cache → the fresh entitled set
        Set<String> modules = resolver.resolve(tenantId).entitledModuleIds();
        producer.publish(new EntitlementChangeMessage(tenantId, modules));
    }
}
