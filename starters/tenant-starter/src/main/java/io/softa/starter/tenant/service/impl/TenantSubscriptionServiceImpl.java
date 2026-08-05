package io.softa.starter.tenant.service.impl;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.service.TenantSubscriptionService;

/**
 * {@link TenantSubscriptionService} — plain typed CRUD for the tenant's owned 1:1 subscription.
 * The owning link is {@code TenantInfo.subscriptionId}; {@code tenantId} on this entity is a convenience
 * back-reference for queries starting from billing, not the link.
 */
@Service
public class TenantSubscriptionServiceImpl extends EntityServiceImpl<TenantSubscription, Long>
        implements TenantSubscriptionService {

    private static final String MODEL = "TenantSubscription";

    @Autowired
    private ModelService<?> modelService;

    @Override
    public boolean updateProjection(TenantSubscription subscription) {
        // Enumerated, not derived from the entity. The point is to name the projected columns and nothing
        // else: deriving the map — even by filtering relations out — would silently pick up whatever field
        // is added to this entity next, which is exactly how the virtual `periods` relation got into a
        // projection write and deleted the rows that write was describing.
        Map<String, Object> row = new LinkedHashMap<>();
        row.put("id", subscription.getId());
        row.put("subscriptionStatus", subscription.getSubscriptionStatus());
        row.put("planId", subscription.getPlanId());
        row.put("periodType", subscription.getPeriodType());
        row.put("currentPeriodId", subscription.getCurrentPeriodId());
        row.put("currentStartDate", subscription.getCurrentStartDate());
        row.put("currentEndDate", subscription.getCurrentEndDate());
        row.put("nextStartDate", subscription.getNextStartDate());
        row.put("projectedForDate", subscription.getProjectedForDate());
        row.put("projectedTime", subscription.getProjectedTime());
        // The nulls stay in the map on purpose: a lapsed tenant has to be able to lose its plan, and a key
        // left out leaves the old value in place.
        return modelService.updateOne(MODEL, row);
    }
}
