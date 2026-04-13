package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import io.softa.framework.orm.constant.FileConstant;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportDataDTO;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.enums.ImportRule;

@Component
public class ImportPersistenceService {

    @Autowired
    private ModelService<?> modelService;

    /**
     * Persist valid import rows according to the import rule.
     */
    public void persist(ImportTemplateDTO importTemplateDTO, ImportDataDTO importDataDTO) {
        List<Map<String, Object>> rows = importDataDTO.getRows();
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        if (!Boolean.TRUE.equals(importTemplateDTO.getSkipException())) {
            persistByRule(importTemplateDTO, rows);
            return;
        }
        // In skipException mode, fallback to row-level persistence so one bad row will not fail the whole import task.
        try {
            persistByRule(importTemplateDTO, rows);
        } catch (RuntimeException ex) {
            persistRowByRow(importTemplateDTO, importDataDTO);
        }
    }

    private void persistByRule(ImportTemplateDTO importTemplateDTO, List<Map<String, Object>> rows) {
        ImportRule importRule = importTemplateDTO.getImportRule();
        if (ImportRule.CREATE_OR_UPDATE.equals(importRule)) {
            modelService.createOrUpdate(importTemplateDTO.getModelName(), rows, importTemplateDTO.getUniqueConstraints());
        } else if (ImportRule.ONLY_CREATE.equals(importRule)) {
            modelService.createList(importTemplateDTO.getModelName(), rows);
        } else if (ImportRule.ONLY_UPDATE.equals(importRule)) {
            modelService.createOrUpdate(importTemplateDTO.getModelName(), rows, importTemplateDTO.getUniqueConstraints());
        }
    }

    private void persistRowByRow(ImportTemplateDTO importTemplateDTO, ImportDataDTO importDataDTO) {
        List<Map<String, Object>> failedRows = importDataDTO.getFailedRows() == null
                ? new ArrayList<>()
                : new ArrayList<>(importDataDTO.getFailedRows());
        Iterator<Map<String, Object>> rowIterator = importDataDTO.getRows().iterator();
        Iterator<Map<String, Object>> originalRowIterator = importDataDTO.getOriginalRows().iterator();
        while (rowIterator.hasNext() && originalRowIterator.hasNext()) {
            Map<String, Object> row = rowIterator.next();
            Map<String, Object> originalRow = originalRowIterator.next();
            try {
                persistByRule(importTemplateDTO, List.of(row));
            } catch (RuntimeException ex) {
                originalRow.put(FileConstant.FAILED_REASON, ex.getMessage());
                failedRows.add(originalRow);
                rowIterator.remove();
                originalRowIterator.remove();
            }
        }
        importDataDTO.setFailedRows(failedRows);
    }
}
