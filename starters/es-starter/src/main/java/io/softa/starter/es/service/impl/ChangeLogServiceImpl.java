package io.softa.starter.es.service.impl;

import java.io.Serializable;
import java.util.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.softa.framework.base.enums.Operator;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.jdbc.pipeline.DataPipelineProxy;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.orm.service.PermissionService;
import io.softa.starter.es.document.ChangeLogDocument;
import io.softa.starter.es.service.ChangeLogService;

import static io.softa.framework.orm.constant.ModelConstant.SLICE_ID;
import static io.softa.framework.orm.enums.AccessType.*;

/**
 * ChangeLog Service Implementation
 */
@Service
public class ChangeLogServiceImpl extends ESServiceImpl<ChangeLog> implements ChangeLogService {

    @Lazy
    @Autowired
    private PermissionService permissionService;

    @Lazy
    @Autowired
    private ModelService<Serializable> modelService;

    @Autowired
    private DataPipelineProxy dataPipelineProxy;

    @Value("${spring.elasticsearch.index.changelog}")
    private String changeLogIndexName;

    /** ES service implementation class must specify the index name by implementing this method */
    public String getIndexName() {
        return changeLogIndexName;
    }

    /**
     * ChangeLog is persisted in ES as {@link ChangeLogDocument} (the two payload
     * maps are flattened to JSON strings on the wire), so query through the
     * document class and translate the page back before returning.
     */
    @Override
    public Page<ChangeLog> searchPage(Filters filters, Orders orders, Page<ChangeLog> page) {
        Page<ChangeLogDocument> docPage = new Page<>(
                page.getPageNumber(), page.getPageSize(), page.isCursorPage(), page.isCount());
        super.searchPage(ChangeLogDocument.class, filters, orders, docPage);
        page.setTotalCount(docPage.getTotalCount());
        page.setRows(docPage.getRows().stream().map(ChangeLogDocument::toChangeLog).toList());
        // Every read of the log passes through here, so this is where its values are masked.
        maskInaccessibleFields(page.getRows());
        return page;
    }

    /**
     * Get the change log by the id of the business data model.
     *
     * @param modelName model name
     * @param id primary key id
     * @param order sort rule based on change time, default is reverse order, only support DESC, ASC string
     * @param includeCreation whether to include data at creation time, default is false, that is, not included
     * @return a page of change log list
     */
    public Page<ChangeLog> getChangeLog(String modelName, Serializable id, Page<ChangeLog> page, String order, boolean includeCreation) {
        // Check if current user has access to the model and id
        permissionService.checkIdsFieldsAccess(modelName, Collections.singletonList(id), null, READ);
        page = this.getRowChangeLog(modelName, id, page, order, includeCreation);
        return this.processChangeLogData(modelName, page, ConvertType.REFERENCE);
    }

    /**
     * Get the change log by the primary key of the timeline model.
     *
     * @param modelName model name
     * @param sliceId primary key of the timeline model
     * @param page page object
     * @param order sort rule based on change time, default is reverse order, only support DESC, ASC string
     * @param includeCreation whether to include data at creation time, default is false, that is, not included
     * @return a page of change log list
     */
    public Page<ChangeLog> getSliceChangeLog(String modelName, Serializable sliceId, Page<ChangeLog> page, String order, boolean includeCreation) {
        // Get the business ids of the timeline model
        List<Serializable> ids = modelService.getIds(modelName, Filters.of(SLICE_ID, Operator.EQUAL, sliceId));
        Assert.notEmpty(ids,
                "Timeline model {0} does not exist slice sliceId={1} data!", modelName, sliceId);
        // Check if current user has access to the timeline model and business id
        permissionService.checkIdsFieldsAccess(modelName, ids, null, READ);
        this.getRowChangeLog(modelName, sliceId, page, order, includeCreation);
        return this.processChangeLogData(modelName, page, ConvertType.REFERENCE);
    }

    /**
     * Get the ChangeLog page with the specified query conditions
     *
     * @param model     model name
     * @param flexQuery query conditions
     * @param page      page object
     * @return a page of list
     */
    public Page<ChangeLog> searchPageByModel(String model, FlexQuery flexQuery, Page<ChangeLog> page) {
        // TODO: Check if current user has access to the model, and append filters of the permission conditions
        Filters filters = Filters.and(flexQuery.getFilters(), new Filters().eq(ChangeLog::getModel, model));
        Orders orders = flexQuery.getOrders();
        this.searchPage(filters, orders, page);
        ConvertType convertType = flexQuery.getConvertType();
        if (ConvertType.REFERENCE.equals(convertType) || ConvertType.DISPLAY.equals(convertType)) {
            // Enhance the field values in before and after data
            this.processChangeLogData(model, page, convertType);
        }
        return page;
    }

    /**
     * Get the ChangeLog list of the specified primary key.
     *
     * @param model     model name
     * @param pKey      primary key, corresponding to the sliceId of the timeline record
     * @param page      page object
     * @param order     sort rule, default is reverse order by change time, only support DESC, ASC string
     * @param includeCreation whether to include data at creation time, default is false, that is, not included
     * @return a page of ChangeLog list
     */
    public Page<ChangeLog> getRowChangeLog(String model, Serializable pKey, Page<ChangeLog> page, String order, boolean includeCreation) {
        // Default sort by changedTime in reverse order
        Orders orders = Orders.DESC.equals(order) ? Orders.ofDesc(ChangeLog::getChangedTime) : Orders.ofAsc(ChangeLog::getChangedTime);
        // Query the change log of the specified filters and rowId
        Filters filters = new Filters().eq(ChangeLog::getModel, model).eq(ChangeLog::getRowId, pKey.toString());
        if (!includeCreation) {
            // When not including the initial creation record, only match UPDATE and DELETE records
            filters.in(ChangeLog::getAccessType, Arrays.asList(UPDATE, DELETE));
        }
        return this.searchPage(filters, orders, page);
    }

    /**
     * Read the data change records according to the id of the business data model.
     *
     * @param modelName model name
     * @param page page object
     * @param convertType convert type
     * @return a page of change log list
     */
    private Page<ChangeLog> processChangeLogData(String modelName, Page<ChangeLog> page, ConvertType convertType) {
        List<Map<String, Object>> changeLogDataList = new ArrayList<>();
        Set<String> fields = new HashSet<>();
        page.getRows().forEach(changeLog -> {
            if (UPDATE.equals(changeLog.getAccessType())) {
                // UPDATE contains data before and after change
                fields.addAll(changeLog.getDataBeforeChange().keySet());
                changeLogDataList.add(changeLog.getDataBeforeChange());
                fields.addAll(changeLog.getDataAfterChange().keySet());
                changeLogDataList.add(changeLog.getDataAfterChange());
            } else if (CREATE.equals(changeLog.getAccessType())) {
                // CREATE only has data after change
                fields.addAll(changeLog.getDataAfterChange().keySet());
                changeLogDataList.add(changeLog.getDataAfterChange());
            }
        });
        // Enhance the field values in before and after data, only retain the fields existing in the metadata.
        fields.retainAll(ModelManager.getModelStoredFields(modelName));
        FlexQuery flexQuery = new FlexQuery(fields);
        flexQuery.setConvertType(convertType);
        dataPipelineProxy.processReadData(modelName, flexQuery, changeLogDataList);
        return page;
    }

    /**
     * Drop from each entry the fields the caller may not see on that record — its before and after
     * values alike, so the entry neither shows the value nor what it was changed from. Each entry is
     * judged as a row of its own model, so a page mixing models is masked correctly.
     *
     * <p>Judged per record, exactly as a read of the record would be: the values are masked by the
     * permission service as rows of the model, keyed by the record's id. A field hidden on either side
     * is removed from both.
     */
    private void maskInaccessibleFields(List<ChangeLog> changeLogs) {
        // One mask call per model for the whole page, not one per entry: each call may ask which of
        // the rows the caller's roles reach.
        Map<String, List<ChangeLog>> byModel = new LinkedHashMap<>();
        for (ChangeLog changeLog : changeLogs) {
            if (changeLog.getModel() != null) {
                byModel.computeIfAbsent(changeLog.getModel(), k -> new ArrayList<>()).add(changeLog);
            }
        }
        byModel.forEach((modelName, logs) -> {
            List<List<Map<String, Object>>> sidesByLog = new ArrayList<>(logs.size());
            List<Map<String, Object>> masked = new ArrayList<>();
            for (ChangeLog changeLog : logs) {
                List<Map<String, Object>> sides = new ArrayList<>(2);
                if (changeLog.getDataBeforeChange() != null) sides.add(changeLog.getDataBeforeChange());
                if (changeLog.getDataAfterChange() != null) sides.add(changeLog.getDataAfterChange());
                sidesByLog.add(sides);
                // Masked as copies carrying the record's id, so the stored maps are only ever pruned.
                for (Map<String, Object> side : sides) {
                    Map<String, Object> copy = new HashMap<>(side);
                    copy.putIfAbsent(ModelConstant.ID, changeLog.getRowId());
                    masked.add(copy);
                }
            }
            if (masked.isEmpty()) return;
            permissionService.maskRows(modelName, masked);
            int next = 0;
            for (List<Map<String, Object>> sides : sidesByLog) {
                Set<String> hidden = new HashSet<>();
                for (Map<String, Object> side : sides) {
                    Map<String, Object> maskedSide = masked.get(next++);
                    for (Map.Entry<String, Object> entry : side.entrySet()) {
                        if (entry.getValue() != null && maskedSide.get(entry.getKey()) == null) {
                            hidden.add(entry.getKey());
                        }
                    }
                }
                if (!hidden.isEmpty()) {
                    sides.forEach(side -> side.keySet().removeAll(hidden));
                }
            }
        });
    }

}
