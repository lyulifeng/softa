package io.softa.starter.file.excel.imports;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Component;

import io.softa.framework.orm.constant.FileConstant;
import io.softa.starter.file.dto.ImportDataDTO;

@Component
public class ImportFailureCollector {

    /**
     * Separate failed rows from valid rows while preserving original row content for failure export.
     */
    public void collect(ImportDataDTO importDataDTO) {
        List<Map<String, Object>> failedRows = new ArrayList<>();
        Iterator<Map<String, Object>> rowIterator = importDataDTO.getRows().iterator();
        Iterator<Map<String, Object>> originalRowIterator = importDataDTO.getOriginalRows().iterator();
        while (rowIterator.hasNext() && originalRowIterator.hasNext()) {
            Map<String, Object> row = rowIterator.next();
            Map<String, Object> originalRow = originalRowIterator.next();
            if (row.containsKey(FileConstant.FAILED_REASON)) {
                originalRow.put(FileConstant.FAILED_REASON, row.get(FileConstant.FAILED_REASON));
                rowIterator.remove();
                originalRowIterator.remove();
                failedRows.add(originalRow);
            }
        }
        importDataDTO.setFailedRows(failedRows);
    }
}
