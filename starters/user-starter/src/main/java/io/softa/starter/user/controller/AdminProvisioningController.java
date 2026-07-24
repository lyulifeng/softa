package io.softa.starter.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.provisioning.AdminProvisioningService;
import io.softa.starter.user.provisioning.CreateAdminRequest;
import io.softa.starter.user.provisioning.CreateAdminResult;

/**
 * Platform (Ops) tenant-admin provisioning. Reachable only by SUPER_ADMIN: TENANT_ADMIN is denied
 * via {@code permission.platform-only-patterns} (/provisioning/**), and a normal user has no
 * permission registered for it. This endpoint creates a tenant's first admin; tenant creation itself
 * is a separate step (the app's shadowed {@code /TenantInfo/createOne}).
 */
@Tag(name = "Tenant Provisioning")
@RestController
@RequestMapping("/provisioning")
public class AdminProvisioningController {

    private final AdminProvisioningService adminProvisioningService;

    public AdminProvisioningController(AdminProvisioningService adminProvisioningService) {
        this.adminProvisioningService = adminProvisioningService;
    }

    @Operation(summary = "Create a tenant's first admin (INVITED account + TENANT_ADMIN role)")
    @PostMapping("/createAdmin")
    public ApiResponse<CreateAdminResult> createAdmin(@RequestBody CreateAdminRequest request) {
        return ApiResponse.success(adminProvisioningService.createAdmin(request));
    }
}
