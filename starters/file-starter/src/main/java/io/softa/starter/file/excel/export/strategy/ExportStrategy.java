package io.softa.starter.file.excel.export.strategy;

import io.softa.starter.file.dto.ExportResult;

/**
 * Strategy for a single-sheet export request.
 */
public interface ExportStrategy {

    ExportMode getMode();

    ExportResult export(ExportContext exportContext);
}
