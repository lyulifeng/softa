package io.softa.starter.message.quota.controller;

import java.time.YearMonth;
import java.util.Map;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.message.quota.dto.MessageQuotaUsageDTO;
import io.softa.starter.message.quota.entity.TenantMessageQuota;
import io.softa.starter.message.quota.service.TenantMessageQuotaService;
import io.softa.starter.message.shared.MonthlyQuotaGuard;
import io.softa.starter.message.shared.TenantScopes;

/**
 * REST controller for tenant message quotas — platform operations only.
 * <p>
 * The single-row write endpoints shadow the generic {@code /{modelName}/...}
 * routes so every UI write passes {@code assertPlatformScope()}: quota is a
 * platform-owned registry about tenants, and no tenant session — role grants
 * regardless — may configure its own ceiling. Programmatic writes hit the same
 * guard inside the service overrides.
 */
@Tag(name = "TenantMessageQuota")
@RestController
@RequestMapping("/TenantMessageQuota")
public class TenantMessageQuotaController
        extends EntityController<TenantMessageQuotaService, TenantMessageQuota, Long> {

    private static final String MODEL_NAME = "TenantMessageQuota";

    @Autowired
    private ModelService<Long> modelService;

    @Autowired
    private MonthlyQuotaGuard quotaGuard;

    @Operation(summary = "Create a tenant quota — platform scope only")
    @PostMapping("/createOne")
    @Transactional
    public ApiResponse<Long> createOne(@RequestBody Map<String, Object> row) {
        service.assertPlatformScope();
        return ApiResponse.success(modelService.createOne(MODEL_NAME, row));
    }

    @Operation(summary = "Create a tenant quota and fetch — platform scope only")
    @PostMapping("/createOneAndFetch")
    @Transactional
    public ApiResponse<Map<String, Object>> createOneAndFetch(@RequestBody Map<String, Object> row) {
        service.assertPlatformScope();
        return ApiResponse.success(modelService.createOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Update a tenant quota — platform scope only")
    @PostMapping("/updateOne")
    @Transactional
    public ApiResponse<Boolean> updateOne(@RequestBody Map<String, Object> row) {
        service.assertPlatformScope();
        return ApiResponse.success(modelService.updateOne(MODEL_NAME, row));
    }

    @Operation(summary = "Update a tenant quota and fetch — platform scope only")
    @PostMapping("/updateOneAndFetch")
    @Transactional
    public ApiResponse<Map<String, Object>> updateOneAndFetch(@RequestBody Map<String, Object> row) {
        service.assertPlatformScope();
        return ApiResponse.success(modelService.updateOneAndFetch(MODEL_NAME, row, ConvertType.REFERENCE));
    }

    @Operation(summary = "Delete a tenant quota — platform scope only; the bucket reverts to the "
            + "deployment defaults")
    @PostMapping("/deleteById")
    public ApiResponse<Boolean> deleteById(@RequestParam Long id) {
        return ApiResponse.success(service.deleteById(id));
    }

    @Operation(summary = "Current-month usage vs resolved limits for one quota bucket",
            description = "tenantId = -1 reads the platform's own bucket. month defaults to the "
                    + "current calendar month (format yyyy-MM). A tenant session may only read "
                    + "its own bucket; any bucket is readable from the platform scope.")
    @GetMapping("/usage")
    public ApiResponse<MessageQuotaUsageDTO> usage(
            @RequestParam Long tenantId,
            @RequestParam(required = false) String month) {
        // Usage is per-tenant consumption data: a tenant may inspect its own
        // bucket, everything else is platform-operations territory.
        if (TenantScopes.multiTenancyEnabled()
                && TenantScopes.currentTenantOrPlatform() != TenantScopes.PLATFORM
                && TenantScopes.currentTenantOrPlatform() != tenantId) {
            service.assertPlatformScope();
        }
        YearMonth target = month != null ? YearMonth.parse(month) : YearMonth.now();
        TenantMessageQuotaService.ResolvedLimits limits = service.resolveLimits(tenantId);
        MessageQuotaUsageDTO dto = new MessageQuotaUsageDTO();
        dto.setTenantId(tenantId);
        dto.setMonth(target.toString());
        dto.setMailUsed(quotaGuard.usage("mail", tenantId, target));
        dto.setMailMonthlyLimit(limits.mailMonthlyLimit());
        dto.setSmsUsed(quotaGuard.usage("sms", tenantId, target));
        dto.setSmsMonthlyLimit(limits.smsMonthlyLimit());
        return ApiResponse.success(dto);
    }
}
