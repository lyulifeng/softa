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

    /**
     * Write the projected columns of one subscription row — the only sanctioned way to update this table.
     *
     * <p>Writes an explicit column map rather than round-tripping the entity, and that is the whole point.
     * The single-argument {@code updateOne(entity)} drops nulls, so a projection could set a column but never
     * clear one; passing {@code ignoreNull = false} fixes that but then serializes <b>every</b> property of
     * the entity — including the virtual {@code periods} relation, as {@code null}. The framework reads
     * "relation key present, value null" as "clear the relation" and deletes every period of that
     * subscription. Since a refresh follows every period write, each newly recorded period was inserted and
     * then deleted by the refresh that was supposed to describe it, with no error anywhere.
     *
     * <p>So: name the columns, include their nulls, and never mention a relation.
     *
     * @param subscription the row to write; its {@code id} identifies it
     * @return whether the row was updated
     */
    boolean updateProjection(TenantSubscription subscription);
}
