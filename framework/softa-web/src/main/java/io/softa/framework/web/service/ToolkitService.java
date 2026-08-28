package io.softa.framework.web.service;

import java.util.Set;

/**
 * Toolkit Service
 */
public interface ToolkitService {

    /**
     * Recompute the stored calculation fields, including computed and cascaded fields.
     *
     * @param modelName model name
     * @param fields fields to be recomputed
     */
    void recompute(String modelName, Set<String> fields);

    /**
     * Encrypts historical plaintext data after the field is set to `encrypted=true`.
     *
     * @param modelName model name
     * @param fieldName field to encrypt historical plaintext data.
     * @return the number of rows fixed
     */
    default Long fixUnencryptedData(String modelName, String fieldName) {
        return fixUnencryptedData(modelName, fieldName, false);
    }

    /**
     * Encrypts historical plaintext data after the field is set to `encrypted=true`.
     * A dry run reports the same number without writing anything, and still verifies that the rows
     * already encrypted decrypt under the configured key - so it answers both questions an operator
     * has before committing: how much data is affected, and is the key the data was written with.
     *
     * @param modelName model name
     * @param fieldName field to encrypt historical plaintext data.
     * @param dryRun true to report the rows that would be encrypted, without writing them
     * @return the number of rows fixed, or that a dry run would fix
     */
    Long fixUnencryptedData(String modelName, String fieldName, boolean dryRun);

}
