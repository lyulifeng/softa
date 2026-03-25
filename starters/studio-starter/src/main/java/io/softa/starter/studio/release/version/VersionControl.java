package io.softa.starter.studio.release.version;

import java.util.List;

import io.softa.starter.studio.release.dto.ModelChangesDTO;

/**
 * Version control for change data
 */
public interface VersionControl {

    /**
     * Collect model-level changes for all version-controlled models from the specified WorkItems.
     *
     * @param appId       app ID used to look up the current DB state
     * @param workItemIds list of WorkItem IDs whose changes to aggregate
     * @return list of model-level change summaries, excluding empty models
     */
    List<ModelChangesDTO> collectModelChanges(Long appId, List<Long> workItemIds);

    /**
     * Get the change data of the model for the specified WorkItems, querying ES changelogs
     * by {@code correlationId IN (workItemIds)} instead of a time-based scan.
     * This is the WorkItem-centric variant used when sealing a version composed of selected WorkItems.
     *
     * @param appId          app ID used to look up the current DB state
     * @param versionedModel version-controlled design model name
     * @param workItemIds    list of WorkItem IDs whose changes to aggregate
     * @return ModelChangesDTO, or {@code null} if there are no changes
     */
    ModelChangesDTO getModelChangesByWorkItems(Long appId, String versionedModel, List<Long> workItemIds);
}
