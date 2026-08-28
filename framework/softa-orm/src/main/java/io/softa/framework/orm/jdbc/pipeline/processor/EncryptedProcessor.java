package io.softa.framework.orm.jdbc.pipeline.processor;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.CollectionUtils;

import io.softa.framework.base.exception.SystemException;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.utils.ListUtils;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.MetaField;

/**
 * Encrypted field processor
 */
@Slf4j
public class EncryptedProcessor extends StringProcessor {

    /** Cap on the row ids named in the plaintext warning, to keep one log line readable. */
    private static final int MAX_REPORTED_IDS = 20;

    public EncryptedProcessor(MetaField metaField, AccessType accessType) {
        super(metaField, accessType);
    }

    /**
     * Batch encryption of encrypted fields for input rows.
     *
     * @param rows      List of rows to be processed
     */
    @Override
    public void batchProcessInputRows(List<Map<String, Object>> rows) {
        // Extract the plaintext dictionary:
        // index-plaintext structure, used for batch encryption, ignoring null values and empty strings
        Map<Integer, String> plaintextMap = ListUtils.extractValueIndexMap(rows, fieldName);
        if (!CollectionUtils.isEmpty(plaintextMap)) {
            // Batch encryption and replacement of plaintext
            Map<Integer, String> ciphertextMap = EncryptUtils.encrypt(plaintextMap);
            ciphertextMap.forEach((index, encryptedValue) -> rows.get(index).put(fieldName, encryptedValue));
        }
    }

    /**
     * Batch decryption of encrypted fields for output rows.
     *
     * @param rows List of rows to be processed
     */
    @Override
    public void batchProcessOutputRows(List<Map<String, Object>> rows) {
        // Extract a map of ciphertext: index-ciphertext for batch decryption, ignoring null and empty strings.
        Map<Integer, String> ciphertextMap = ListUtils.extractValueIndexMap(rows, fieldName);
        if (CollectionUtils.isEmpty(ciphertextMap)) {
            return;
        }
        Map<Integer, String> plaintextMap;
        try {
            // Batch decryption and replacement of ciphertext
            plaintextMap = EncryptUtils.decrypt(ciphertextMap);
        } catch (Exception e) {
            // Name the column the caller has to look at: the algorithm only sees an opaque value
            throw new SystemException("Model field {0}: {1} cannot be decrypted.", modelName, fieldName, e);
        }
        plaintextMap.forEach((index, plaintext) -> rows.get(index).put(fieldName, plaintext));
        if (plaintextMap.size() < ciphertextMap.size()) {
            // Values left out of the result were never ciphertext, so they are stored as plaintext and
            // are returned as read. Report where they are, so that the rows can be re-encrypted.
            warnPlaintextRows(rows, ciphertextMap.keySet(), plaintextMap.keySet());
        }
    }

    /**
     * Warns about rows whose encrypted field holds a plaintext value, naming the ids to repair.
     *
     * @param rows        List of processed rows
     * @param readIndexes Indexes of the rows a value was read from
     * @param decryptedIndexes Indexes of the rows the value was decrypted for
     */
    private void warnPlaintextRows(List<Map<String, Object>> rows, Set<Integer> readIndexes,
                                   Set<Integer> decryptedIndexes) {
        int plaintextCount = readIndexes.size() - decryptedIndexes.size();
        String ids = readIndexes.stream()
                .filter(index -> !decryptedIndexes.contains(index))
                .sorted()
                .limit(MAX_REPORTED_IDS)
                .map(index -> String.valueOf(rows.get(index).get(ModelConstant.ID)))
                .collect(Collectors.joining(", "));
        log.warn("Model field {}: {} holds a plaintext value in {} of {} rows read, returned as stored. "
                        + "Re-encrypt them by saving the rows again. Row ids: [{}{}]",
                modelName, fieldName, plaintextCount, readIndexes.size(), ids,
                plaintextCount > MAX_REPORTED_IDS ? ", ..." : "");
    }

}
