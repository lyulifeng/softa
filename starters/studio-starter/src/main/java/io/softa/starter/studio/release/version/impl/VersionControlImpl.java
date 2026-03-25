package io.softa.starter.studio.release.version.impl;

import java.io.Serializable;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.utils.DateUtils;
import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.es.service.ChangeLogService;
import io.softa.starter.metadata.constant.MetadataConstant;
import io.softa.starter.studio.release.constant.VersionConstant;
import io.softa.starter.studio.release.dto.ModelChangesDTO;
import io.softa.starter.studio.release.dto.RowChangeDTO;
import io.softa.starter.studio.release.version.VersionControl;

import static io.softa.framework.orm.enums.AccessType.UPDATE;

/**
 * Version control implementation
 */
@Component
public class VersionControlImpl implements VersionControl {

    @Autowired
    private ChangeLogService changeLogService;

    @Autowired
    private ModelService<Long> modelService;

    /**
     * Collect model-level changes for all version-controlled models from the specified WorkItems.
     *
     * @param appId       app ID used to look up the current DB state
     * @param workItemIds list of WorkItem IDs whose changes to aggregate
     * @return list of model-level change summaries, excluding empty models
     */
    @Override
    public List<ModelChangesDTO> collectModelChanges(Long appId, List<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return List.of();
        }
        List<String> versionedModels = new ArrayList<>(MetadataConstant.VERSION_CONTROL_MODELS.keySet());
        List<ChangeLog> allChangeLogs = changeLogService.searchByCorrelationIds(
                versionedModels, toCorrelationIds(workItemIds));
        if (allChangeLogs.isEmpty()) {
            return List.of();
        }
        Map<String, List<ChangeLog>> logsByModel = allChangeLogs.stream()
                .collect(Collectors.groupingBy(ChangeLog::getModel, LinkedHashMap::new, Collectors.toList()));
        List<ModelChangesDTO> result = new ArrayList<>();
        for (String versionedModel : versionedModels) {
            ModelChangesDTO changes = buildModelChanges(appId, versionedModel, logsByModel.get(versionedModel));
            if (changes != null) {
                result.add(changes);
            }
        }
        return result;
    }

    /**
     * Get the change data of the model for the specified WorkItems, querying ES changelogs
     * by {@code correlationId IN (workItemIds)} instead of a time-based scan.
     *
     * @param appId          app ID used to look up the current DB state
     * @param versionedModel version-controlled design model name
     * @param workItemIds    list of WorkItem IDs whose changes to aggregate
     * @return ModelChangesDTO, or {@code null} if there are no changes
     */
    @Override
    public ModelChangesDTO getModelChangesByWorkItems(Long appId, String versionedModel, List<Long> workItemIds) {
        if (workItemIds == null || workItemIds.isEmpty()) {
            return null;
        }
        List<ChangeLog> allChangeLogs = changeLogService.searchByCorrelationIds(versionedModel, toCorrelationIds(workItemIds));
        return buildModelChanges(appId, versionedModel, allChangeLogs);
    }

    private List<String> toCorrelationIds(List<Long> workItemIds) {
        return workItemIds.stream()
                .map(String::valueOf)
                .collect(Collectors.toList());
    }

    private ModelChangesDTO buildModelChanges(Long appId, String versionedModel, List<ChangeLog> allChangeLogs) {
        if (allChangeLogs == null || allChangeLogs.isEmpty()) {
            return null;
        }

        // Group changelogs by rowId, preserving insertion order (changedTime ASC from ES)
        Map<Serializable, List<ChangeLog>> logsByRow = allChangeLogs.stream()
                .collect(Collectors.groupingBy(ChangeLog::getRowId, LinkedHashMap::new, Collectors.toList()));

        // Determine which rowIds need a current-data DB lookup (those not ending in DELETE)
        Set<Serializable> rowIdsNeedingDbLookup = new HashSet<>();
        for (Map.Entry<Serializable, List<ChangeLog>> entry : logsByRow.entrySet()) {
            AccessType lastType = entry.getValue().getLast().getAccessType();
            if (lastType != AccessType.DELETE) {
                rowIdsNeedingDbLookup.add(entry.getKey());
            }
        }

        // Fetch the current DB state for non-deleted rows
        Map<Serializable, Map<String, Object>> currentDataMap = new HashMap<>();
        if (!rowIdsNeedingDbLookup.isEmpty()) {
            List<Map<String, Object>> dbRows = getCurrentDataByIds(appId, versionedModel, rowIdsNeedingDbLookup);
            dbRows.forEach(row -> currentDataMap.put((Serializable) row.get(ModelConstant.ID), row));
        }

        ModelChangesDTO modelChangesDTO = new ModelChangesDTO(versionedModel);
        for (Map.Entry<Serializable, List<ChangeLog>> entry : logsByRow.entrySet()) {
            Serializable rowId = entry.getKey();
            List<ChangeLog> logs = entry.getValue();
            AccessType firstType = logs.getFirst().getAccessType();
            AccessType lastType = logs.getLast().getAccessType();

            if (firstType == AccessType.CREATE && lastType == AccessType.DELETE) {
                // Row was created and deleted within the same WorkItem set — net effect: no change
                continue;
            } else if (lastType == AccessType.DELETE) {
                // Row existed before this WorkItem set and was deleted.
                // Use the DELETE log's dataBeforeChange as the final row state.
                ChangeLog deleteLog = logs.getLast();
                RowChangeDTO rowChangeDTO = new RowChangeDTO(versionedModel, (Long) rowId);
                rowChangeDTO.setAccessType(AccessType.DELETE);
                rowChangeDTO.setCurrentData(deleteLog.getDataBeforeChange());
                rowChangeDTO.setLastChangedById(deleteLog.getChangedById());
                rowChangeDTO.setLastChangedBy(deleteLog.getChangedBy());
                rowChangeDTO.setLastChangedTime(deleteLog.getChangedTime());
                modelChangesDTO.addDeletedRow(rowChangeDTO);
            } else if (firstType == AccessType.CREATE) {
                // Row was created within this WorkItem set.
                Map<String, Object> currentData = currentDataMap.get(rowId);
                if (currentData == null) {
                    // Row was created but subsequently deleted outside this WorkItem set — skip
                    continue;
                }
                modelChangesDTO.addCreatedRow(convertToRowChangeDTO(versionedModel, currentData));
            } else {
                // Row existed before and was updated within this WorkItem set.
                Map<String, Object> currentData = currentDataMap.get(rowId);
                if (currentData == null) {
                    // Row no longer exists in DB — skip
                    continue;
                }
                RowChangeDTO rowChangeDTO = mergeUpdatedToRowChangeDTO(logs, currentData);
                modelChangesDTO.addUpdatedRow(rowChangeDTO);
            }
        }

        if (modelChangesDTO.getCreatedRows().isEmpty() && modelChangesDTO.getUpdatedRows().isEmpty()
                && modelChangesDTO.getDeletedRows().isEmpty()) {
            return null;
        }
        return modelChangesDTO;
    }

    /**
     * Get the current DB state for specific row IDs within the given app.
     * Used by the WorkItem-centric path to look up current data for created/updated rows.
     *
     * @param appId          app ID
     * @param versionedModel version-controlled design model name
     * @param rowIds         specific row IDs to fetch
     * @return list of current row data maps
     */
    private List<Map<String, Object>> getCurrentDataByIds(Long appId, String versionedModel,
            Collection<Serializable> rowIds) {
        Filters filters = new Filters()
                .eq(VersionConstant.APP_ID, appId)
                .in(ModelConstant.ID, rowIds);
        if (ModelManager.isSoftDeleted(versionedModel)) {
            String softDeleteField = ModelManager.getSoftDeleteField(versionedModel);
            filters.in(softDeleteField, Arrays.asList(false, true, null));
        }
        Set<String> fields = ModelManager.getModelFieldsWithoutXToMany(versionedModel);
        FlexQuery flexQuery = new FlexQuery(fields, filters);
        return modelService.searchList(versionedModel, flexQuery);
    }

    /**
     * Convert the changed data to RowChangeDTO
     *
     * @param modelName Model name
     * @param row Changed data
     * @return RowChangeDTO object
     */
    private static RowChangeDTO convertToRowChangeDTO(String modelName, Map<String, Object> row) {
        RowChangeDTO rowChangeDTO = new RowChangeDTO(modelName, (Long) row.get(ModelConstant.ID));
        rowChangeDTO.setAccessType(AccessType.CREATE);
        rowChangeDTO.setCurrentData(row);
        rowChangeDTO.setLastChangedById((Long) row.get(ModelConstant.UPDATED_ID));
        rowChangeDTO.setLastChangedBy((String) row.get(ModelConstant.UPDATED_BY));
        rowChangeDTO.setLastChangedTime(DateUtils.dateTimeToString(row.get(ModelConstant.UPDATED_TIME)));
        return rowChangeDTO;
    }

    /**
     * Merge List<ChangeLog> with the same id into one RowChangeDTO
     *
     * @param changeLogs List of change records with the same id, sorted in ascending order
     * @param currentData Current data
     * @return RowChangeDTO object
     */
    private static RowChangeDTO mergeUpdatedToRowChangeDTO(List<ChangeLog> changeLogs, Map<String, Object> currentData) {
        ChangeLog lastLog = changeLogs.getLast();
        RowChangeDTO rowChangeDTO = new RowChangeDTO(lastLog.getModel(), (Long) lastLog.getRowId());
        rowChangeDTO.setAccessType(UPDATE);
        rowChangeDTO.setCurrentData(currentData);
        rowChangeDTO.setLastChangedById(lastLog.getChangedById());
        rowChangeDTO.setLastChangedBy(lastLog.getChangedBy());
        rowChangeDTO.setLastChangedTime(lastLog.getChangedTime());
        // Merge data before the change in DESC order
        for (int i = changeLogs.size() - 1; i >= 0; i--) {
            ChangeLog changeLog = changeLogs.get(i);
            rowChangeDTO.mergeDataBeforeChange(changeLog.getDataBeforeChange());
        }
        // Merge data after the change in ASC order
        for (ChangeLog changeLog : changeLogs) {
            rowChangeDTO.mergeDataAfterChange(changeLog.getDataAfterChange());
        }
        return rowChangeDTO;
    }

}
