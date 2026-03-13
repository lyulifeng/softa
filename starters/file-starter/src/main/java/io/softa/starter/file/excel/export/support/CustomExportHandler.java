package io.softa.starter.file.excel.export.support;

import java.util.List;
import java.util.Map;

/**
 * CustomExportHandler for custom business logic.
 * Implementations may mutate row contents, but should not replace the row maps.
 */
public interface CustomExportHandler {

    /**
     * Handle the export data rows before they are written to the file.
     *
     * @param rows export data rows
     */
    void handleExportData(List<Map<String, Object>> rows);
}
