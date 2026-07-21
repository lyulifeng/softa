package io.softa.starter.tenant.service.impl;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.service.TenantSubscriptionService;

/**
 * {@link TenantSubscriptionService} — plain typed CRUD for the tenant's owned 1:1 subscription.
 * The owning link is {@code TenantInfo.subscriptionId}; there is no back key on this entity.
 */
@Service
public class TenantSubscriptionServiceImpl extends EntityServiceImpl<TenantSubscription, Long>
        implements TenantSubscriptionService {
}
