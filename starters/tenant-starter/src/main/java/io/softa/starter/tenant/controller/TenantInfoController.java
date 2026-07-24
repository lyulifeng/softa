package io.softa.starter.tenant.controller;

import java.util.Map;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.utils.IdUtils;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.tenant.entitlement.TenantEntitlementChangedEvent;
import io.softa.starter.tenant.provisioning.ProvisionTenantRequest;
import io.softa.starter.tenant.provisioning.TenantProvisioningService;

/**
 * Shadows the generic {@code /{modelName}} write endpoints for TenantInfo (Spring routes the literal
 * path over the templated {@code ModelController} mapping by specificity).
 * <ul>
 *   <li>{@code createOne} — tenant creation must provision (registry row + owned version + seed),
 *       not bare-insert; delegates to {@link TenantProvisioningService}.</li>
 *   <li>{@code updateOne} / {@code updateOneAndFetch} — the owned 1:1 version is edited inline on the
 *       Tenant Info form. A plain update cascades into {@code TenantSubscription} but does NOT refresh
 *       entitlement, so when the payload touched the version ({@code subscriptionId}) we publish
 *       {@link TenantEntitlementChangedEvent} to evict the {@code entl:} cache + MQ-fan the fresh
 *       module set — otherwise a plan / lifecycle change wouldn't take effect until the 1h TTL.</li>
 * </ul>
 * Platform-only: {@code /TenantInfo/**} is in {@code permission.platform-only-patterns}.
 */
@Tag(name = "Tenant Provisioning")
@RestController
@RequestMapping("/TenantInfo")
public class TenantInfoController {

    private static final String MODEL = "TenantInfo";
    /** The owned-version relation field; its presence in an update payload = the version changed. */
    private static final String VERSION_FIELD = "subscriptionId";

    private final TenantProvisioningService provisioningService;
    private final ModelService<Long> modelService;
    private final ApplicationEventPublisher eventPublisher;

    public TenantInfoController(TenantProvisioningService provisioningService,
                               ModelService<Long> modelService,
                               ApplicationEventPublisher eventPublisher) {
        this.provisioningService = provisioningService;
        this.modelService = modelService;
        this.eventPublisher = eventPublisher;
    }

    @Operation(summary = "Create a tenant (registry row + owned version + per-tenant seed)")
    @PostMapping("/createOne")
    public ApiResponse<Long> createOne(@RequestBody ProvisionTenantRequest request) {
        return ApiResponse.success(provisioningService.provision(request).tenantId());
    }

    @Operation(summary = "Update a tenant — refreshes entitlement when the inline version changed")
    @PostMapping("/updateOne")
    public ApiResponse<Boolean> updateOne(@RequestBody Map<String, Object> row) {
        Assert.notEmpty(row, "The data to be updated cannot be empty!");
        Assert.notNull(row.get("id"), "`id` cannot be null or missing when updating data!");
        IdUtils.formatMapId(MODEL, row);
        boolean ok = modelService.updateOne(MODEL, row);
        refreshEntitlementIfVersionTouched(row);
        return ApiResponse.success(ok);
    }

    @Operation(summary = "Update a tenant and fetch — refreshes entitlement when the inline version changed")
    @PostMapping("/updateOneAndFetch")
    public ApiResponse<Map<String, Object>> updateOneAndFetch(@RequestBody Map<String, Object> row) {
        Assert.notEmpty(row, "The data to be updated cannot be empty!");
        Assert.notNull(row.get("id"), "`id` cannot be null or missing when updating data!");
        IdUtils.formatMapId(MODEL, row);
        Map<String, Object> result = modelService.updateOneAndFetch(MODEL, row, ConvertType.REFERENCE);
        refreshEntitlementIfVersionTouched(row);
        return ApiResponse.success(result);
    }

    /**
     * When the update payload carried the owned version ({@code subscriptionId}): first reconcile a
     * future start date (active sub + future {@code effectiveFrom} → SCHEDULED, so an inline edit with a
     * future start defers like provisioning does), then publish the entitlement-changed event so the
     * listener evicts {@code entl:} + fans the fresh module set to MQ. Runs after the update committed;
     * the listener's {@code fallbackExecution} covers the no-active-transaction case.
     */
    private void refreshEntitlementIfVersionTouched(Map<String, Object> row) {
        if (!row.containsKey(VERSION_FIELD)) {
            return;
        }
        Long tenantId = IdUtils.convertIdToLong(row.get("id"));
        if (tenantId != null) {
            provisioningService.reconcileScheduledStart(tenantId);
            eventPublisher.publishEvent(new TenantEntitlementChangedEvent(tenantId));
        }
    }
}
