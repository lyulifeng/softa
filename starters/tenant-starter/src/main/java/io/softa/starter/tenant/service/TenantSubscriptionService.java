package io.softa.starter.tenant.service;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.tenant.entity.TenantSubscription;

/**
 * Typed CRUD for the tenant's owned 1:1 version/subscription. The version lives here (not on the
 * TenantInfo columns) and is linked from {@code TenantInfo.subscriptionId}, so version management
 * stays optional for apps that don't need it.
 *
 * <p>Mutations flow through the tenant create/edit surface ({@code TenantInfoController}): create =
 * provisioning (explicit createOne + link), edit = {@code /TenantInfo/updateOne} with the inline
 * {@code subscriptionId} object, which the ORM cascade-updates and which republishes the
 * entitlement-changed event. There is deliberately no bespoke plan/lifecycle endpoint here.
 */
public interface TenantSubscriptionService extends EntityService<TenantSubscription, Long> {
}
