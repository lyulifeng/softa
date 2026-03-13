package io.softa.starter.file.excel.imports;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import io.softa.framework.orm.service.ModelService;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.enums.ImportRule;

@Component
public class ImportPersistenceService {

    @Autowired
    private ModelService<?> modelService;

    /**
     * Persist valid import rows according to the import rule.
     */
    public void persist(ImportTemplateDTO importTemplateDTO, List<Map<String, Object>> rows) {
        if (CollectionUtils.isEmpty(rows)) {
            return;
        }
        ImportRule importRule = importTemplateDTO.getImportRule();
        if (ImportRule.CREATE_OR_UPDATE.equals(importRule)) {
            modelService.createOrUpdate(importTemplateDTO.getModelName(), rows, importTemplateDTO.getUniqueConstraints());
        } else if (ImportRule.ONLY_CREATE.equals(importRule)) {
            modelService.createList(importTemplateDTO.getModelName(), rows);
        } else if (ImportRule.ONLY_UPDATE.equals(importRule)) {
            modelService.createOrUpdate(importTemplateDTO.getModelName(), rows, importTemplateDTO.getUniqueConstraints());
        }
    }
}
