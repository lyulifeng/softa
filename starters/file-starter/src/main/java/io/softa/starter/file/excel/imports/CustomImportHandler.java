package io.softa.starter.file.excel.imports;

import java.util.List;
import java.util.Map;

import io.softa.framework.orm.constant.FileConstant;

/**
 * Custom import business hook.
 *
 * <p>Contract:
 * implementations may mutate row values in-place and may mark a row as failed by setting
 * {@link FileConstant#FAILED_REASON}, but must not add, remove, reorder or replace row objects.
 * This keeps the current rows list aligned with the copied original rows for later failure export.</p>
 */
public interface CustomImportHandler {

    /**
     * Handle import rows in-place.
     *
     * @param rows import rows, mutable in-place only
     * @param env environment variables
     */
    void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env);
}
