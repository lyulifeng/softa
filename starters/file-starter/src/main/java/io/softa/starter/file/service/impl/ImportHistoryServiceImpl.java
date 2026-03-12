package io.softa.starter.file.service.impl;

import java.util.List;
import java.util.Map;
import org.springframework.stereotype.Service;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.file.entity.ImportHistory;
import io.softa.starter.file.service.ImportHistoryService;

/**
 * ImportHistory service implementation
 */
@Service
public class ImportHistoryServiceImpl extends EntityServiceImpl<ImportHistory, Long> implements ImportHistoryService {

    @Override
    public List<Map<String, Object>> listMyImportHistory(String modelName) {
        Long userId = ContextHolder.getContext().getUserId();
        FlexQuery flexQuery = new FlexQuery()
                .where(new Filters()
                        .eq(ImportHistory::getCreatedId, userId)
                        .eq(ImportHistory::getModelName, modelName))
                .orderBy(Orders.ofDesc(ImportHistory::getCreatedTime))
                .setConvertType(ConvertType.REFERENCE);
        return this.modelService.searchList(this.modelName, flexQuery);
    }
}
