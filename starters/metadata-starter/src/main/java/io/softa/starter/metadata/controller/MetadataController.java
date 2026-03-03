package io.softa.starter.metadata.controller;

import java.util.List;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import io.softa.framework.base.enums.SystemUser;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.annotation.SwitchUser;
import io.softa.framework.web.dto.MetadataUpgradePackage;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.metadata.controller.dto.MetaModelDTO;
import io.softa.starter.metadata.service.MetadataService;

/**
 * Metadata Upgrade controller
 */
@Tag(name = "Metadata API")
@RestController
@RequestMapping("/metadata")
public class MetadataController {

    @Autowired
    private MetadataService metadataService;

    /**
     * Get the MetaModel object by modelName
     *
     * @param modelName model name
     * @return metaModel object
     */
    @GetMapping("/getMetaModel")
    @Operation(summary = "getMetaModel", description = "Get the MetaModel object by modelName")
    @Parameter(name = "modelName", description = "Model name", required = true)
    public ApiResponse<MetaModelDTO> getMetaModelDTO(String modelName) {
        Assert.notBlank(modelName, "Model name cannot be empty.");
        return ApiResponse.success(metadataService.getMetaModelDTO(modelName));
    }

    /**
     * Upgrades the metadata of multiple models, all within a single transaction
     * to avoid refreshing the model pool repeatedly and missing dependency data.
     *
     * @param metadataPackages the metadata packages to upgrade
     * @return Success or not
     */
    @Operation(summary = "upgrade")
    @PostMapping("/upgrade")
    @SwitchUser(value = SystemUser.INTEGRATION_USER)
    public ApiResponse<Boolean> upgradePackage(@RequestBody List<MetadataUpgradePackage> metadataPackages) {
        Assert.notEmpty(metadataPackages, "Metadata upgrade data must not be empty!");
        metadataService.upgradeMetadata(metadataPackages);
        metadataService.reloadMetadata();
        return ApiResponse.success(true);
    }

    /**
     * Reload metadata.
     * The current replica will be unavailable if an exception occurs during the reload,
     * and the metadata needs to be fixed and reloaded.
     */
    @Operation(summary = "reload")
    @PostMapping("/reload")
    public ApiResponse<Boolean> reloadModelManager() {
        metadataService.reloadMetadata();
        return ApiResponse.success(true);
    }

}