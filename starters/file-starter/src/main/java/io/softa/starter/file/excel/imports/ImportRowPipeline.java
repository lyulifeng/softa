package io.softa.starter.file.excel.imports;

import java.util.List;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.NoSuchBeanDefinitionException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.SpringContextUtils;
import io.softa.framework.base.utils.StringTools;
import io.softa.starter.file.dto.ImportDataDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.excel.imports.handler.BaseImportHandler;

@Component
public class ImportRowPipeline {

    @Autowired
    private ImportHandlerFactory importHandlerFactory;

    @Autowired
    private ImportFailureCollector importFailureCollector;

    @Autowired
    private ImportPersistenceService importPersistenceService;

    /**
     * Run the full import row pipeline: standard handlers, custom handler, failure collection, persistence.
     */
    public void importData(ImportTemplateDTO importTemplateDTO, ImportDataDTO importDataDTO) {
        List<BaseImportHandler> handlers = importHandlerFactory.createHandlers(importTemplateDTO);
        for (BaseImportHandler handler : handlers) {
            handler.handleRows(importDataDTO.getRows());
        }
        executeCustomHandler(importTemplateDTO.getCustomHandler(), importDataDTO);
        importFailureCollector.collect(importDataDTO);
        importPersistenceService.persist(importTemplateDTO, importDataDTO.getRows());
    }

    private void executeCustomHandler(String handlerName, ImportDataDTO importDataDTO) {
        if (StringUtils.isBlank(handlerName)) {
            return;
        }
        if (!StringTools.isBeanName(handlerName)) {
            throw new IllegalArgumentException("The name of custom import handler `{0}` is invalid.", handlerName);
        }
        try {
            CustomImportHandler handler = SpringContextUtils.getBean(handlerName, CustomImportHandler.class);
            List<Map<String, Object>> rows = importDataDTO.getRows();
            int originalSize = rows.size();
            List<Integer> rowIdentitySnapshot = rows.stream().map(System::identityHashCode).toList();
            handler.handleImportData(rows, importDataDTO.getEnv());
            validateCustomHandlerContract(handlerName, rows, originalSize, rowIdentitySnapshot);
        } catch (NoSuchBeanDefinitionException e) {
            throw new IllegalArgumentException("The custom import handler `{0}` is not found.", handlerName);
        }
    }

    void validateCustomHandlerContract(String handlerName, List<Map<String, Object>> rows, int originalSize,
                                       List<Integer> rowIdentitySnapshot) {
        if (rows.size() != originalSize) {
            throw new IllegalArgumentException(
                    "The custom import handler `{0}` must not add or remove rows.", handlerName);
        }
        for (int i = 0; i < rows.size(); i++) {
            if (System.identityHashCode(rows.get(i)) != rowIdentitySnapshot.get(i)) {
                throw new IllegalArgumentException(
                        "The custom import handler `{0}` must not reorder or replace row objects.", handlerName);
            }
        }
    }
}
