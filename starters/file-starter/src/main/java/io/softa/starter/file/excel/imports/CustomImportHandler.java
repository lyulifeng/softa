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
     * @param validateOnly true while the validation-only pipeline runs: the handler still runs — its
     *                     checks are part of the validation feedback the user sees — but must skip
     *                     everything that writes (provisioning accounts, scheduling jobs, calling out).
     *                     Passed as an argument rather than left in {@code env} so an implementation
     *                     neither needs the reserved key's name nor has to decide what an absent or
     *                     non-Boolean value means.
     */
    void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env, boolean validateOnly);

    /**
     * @deprecated Implement {@link #handleImportData(List, Map, boolean)} instead — without the flag a
     *         handler cannot tell a real import from a validation run, so anything it writes happens
     *         twice: once while the user is only asking whether the file is valid. Kept so an existing
     *         implementation still compiles; the pipeline never calls this overload.
     */
    @Deprecated(forRemoval = true)
    default void handleImportData(List<Map<String, Object>> rows, Map<String, Object> env) {
        handleImportData(rows, env, false);
    }
}
