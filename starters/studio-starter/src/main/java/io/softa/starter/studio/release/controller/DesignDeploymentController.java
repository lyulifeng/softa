package io.softa.starter.studio.release.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.*;

import io.softa.framework.web.controller.EntityController;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.studio.release.dto.DeploymentPreviewDTO;
import io.softa.starter.studio.release.entity.DesignDeployment;
import io.softa.starter.studio.release.service.DesignDeploymentService;

/**
 * DesignDeployment Model Controller.
 * <p>
 * The main deployment entry point. Deploy a Version to an Env and the system automatically
 * merges released versions by sealedTime, generates DDL, and executes the deployment — all in one step.
 */
@Tag(name = "DesignDeployment")
@RestController
@RequestMapping("/DesignDeployment")
public class DesignDeploymentController extends EntityController<DesignDeploymentService, DesignDeployment, Long> {

    /**
     * Deploy a Version to an Env.
     * The system automatically merges released versions in the sealedTime interval
     * from env.currentVersionId to targetVersionId, generates DDL, and executes the deployment.
     */
    @Operation(description = "Deploy a sealed/frozen Version to an Env. " +
            "Automatically merges released versions by sealedTime, generates DDL, and executes the deployment.")
    @PostMapping(value = "/deployToEnv")
    public ApiResponse<Long> deployToEnv(
            @RequestParam @Parameter(description = "Target Environment ID") Long envId,
            @RequestParam @Parameter(description = "Target Version ID") Long targetVersionId) {
        return ApiResponse.success(service.deployToEnv(envId, targetVersionId));
    }

    /**
     * Retry a failed deployment.
     */
    @Operation(description = "Retry a failed deployment by creating a new Deployment with the same parameters.")
    @PostMapping(value = "/retry")
    @Parameter(name = "deploymentId", description = "Deployment ID")
    public ApiResponse<Long> retry(@RequestParam Long deploymentId) {
        return ApiResponse.success(service.retryDeployment(deploymentId));
    }

    /**
     * Preview the deployment content: merged changes, DDL, preflight checks.
     */
    @Operation(description = "Preview the deployment content.")
    @GetMapping(value = "/previewDeployment")
    @Parameter(name = "deploymentId", description = "Deployment ID")
    public ApiResponse<DeploymentPreviewDTO> previewDeployment(@RequestParam Long deploymentId) {
        return ApiResponse.success(service.previewDeployment(deploymentId));
    }

    /**
     * Preview DDL SQL of a deployment (table + index).
     * The returned SQL can be copied to a database client for execution.
     */
    @Operation(description = "Preview DDL SQL of a deployment (table + index), ready for copy to database client.")
    @GetMapping(value = "/previewDDL")
    @Parameter(name = "deploymentId", description = "Deployment ID")
    public ApiResponse<String> previewDDL(@RequestParam Long deploymentId) {
        return ApiResponse.success(service.previewDeploymentDDL(deploymentId));
    }

}
