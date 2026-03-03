package io.softa.starter.metadata.service;

import java.util.List;

import io.softa.framework.web.dto.MetadataUpgradePackage;
import io.softa.starter.metadata.controller.dto.MetaModelDTO;

/**
 * Metadata Upgrade Service.
 */
public interface MetadataService {

    /**
     * Get the MetaModelDTO object by modelName
     *
     * @param modelName model name
     * @return metaModelDTO object
     */
    MetaModelDTO getMetaModelDTO(String modelName);

    /**
     /**
     * Upgrades the metadata of multiple models, all within a single transaction
     * to avoid refreshing the model pool repeatedly and missing dependency data.
     *
     * @param metadataPackages the metadata packages to Upgrade
     */
    void upgradeMetadata(List<MetadataUpgradePackage> metadataPackages);

    /**
     * Reload metadata.
     * The current replica will be unavailable if an exception occurs during the reload,
     * and the metadata needs to be fixed and reloaded.
     */
    void reloadMetadata();
}
