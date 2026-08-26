package io.softa.starter.file.service.impl;

import java.util.List;
import java.util.Map;
import java.util.HashSet;
import java.util.Set;
import org.springframework.stereotype.Service;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Orders;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.file.entity.ImportHistory;
import io.softa.starter.file.service.ImportHistoryService;

/**
 * ImportHistory service implementation
 */
@Service
public class ImportHistoryServiceImpl extends EntityServiceImpl<ImportHistory, Long> implements ImportHistoryService {

    /**
     * The imports this page can show, which is the imports this page can START.
     *
     * <p>{@code ImportTemplateController.listByModel} offers a model's own templates AND its child
     * models' — that is how one employee page hands out the templates for addresses, family members
     * and the rest. The history asked for the model alone, so those imports ran from that page and
     * then were nowhere on it: the file uploaded, the rows landed, and the list said nothing had
     * happened. The only way to see them was a SQL client.
     *
     * <p>So it reads the same set the template list offers. The two answer one question — what was
     * imported from here — and were answering it differently.
     */
    @Override
    public List<Map<String, Object>> listMyImportHistory(String modelName) {
        Long userId = ContextHolder.getContext().getUserId();
        // Copied rather than added to in place: whether getChildModels hands back something mutable
        // is not part of what it promises.
        Set<String> modelNames = new HashSet<>(ModelManager.getChildModels(modelName));
        modelNames.add(modelName);
        FlexQuery flexQuery = new FlexQuery()
                .where(new Filters()
                        .eq(ImportHistory::getCreatedId, userId)
                        .in(ImportHistory::getModelName, modelNames))
                .orderBy(Orders.ofDesc(ImportHistory::getCreatedTime))
                .setConvertType(ConvertType.REFERENCE);
        return this.modelService.searchList(this.modelName, flexQuery);
    }
}
