package io.softa.framework.web.task.handlers;

import java.util.*;
import com.google.common.collect.Sets;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;

import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.ListUtils;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.task.AsyncTaskHandler;
import io.softa.framework.web.task.AsyncTaskHandlerList;
import io.softa.framework.web.task.params.PlaintextToCiphertextParams;

import static io.softa.framework.orm.constant.ModelConstant.ID;
import static io.softa.framework.orm.constant.ModelConstant.SLICE_ID;

/**
 * Ciphertext to Plaintext Handler
 */
@Component
public class PlaintextToCiphertextHandler implements AsyncTaskHandler<PlaintextToCiphertextParams> {

    @Autowired
    private ModelService<?> modelService;

    /**
     * Get the code of the asynchronous task handler.
     * @return The code of the asynchronous task handler.
     */
    @Override
    public String getAsyncTaskHandlerCode() {
        return AsyncTaskHandlerList.PLAINTEXT_TO_CIPHERTEXT.getCode();
    }

    /**
     * Get the parameter type required by the current handler.
     * @return The class type of the parameters.
     */
    @Override
    public Class<PlaintextToCiphertextParams> getParamsType() {
        return PlaintextToCiphertextParams.class;
    }

    /**
     * Validate the data integrity of the asynchronous task parameters.
     * @param taskParams The parameters of the task to validate.
     */
    @Override
    public void validateParams(PlaintextToCiphertextParams taskParams) {
        Assert.notBlank(taskParams.getModel(),
                "Asynchronous task {0} model name parameter cannot be empty!", getAsyncTaskHandlerCode());
        Assert.allNotBlank(taskParams.getFields(),
                "Asynchronous task {0} fields parameter cannot be empty or contain null values! {1}",
                getAsyncTaskHandlerCode(), taskParams.getFields());
        Assert.allNotNull(taskParams.getIds(),
                "Asynchronous task {0} IDs parameter cannot be empty or contain null values! {1}",
                getAsyncTaskHandlerCode(), taskParams.getIds());
        for (String field : taskParams.getFields()) {
            MetaField metaField = ModelManager.getModelField(taskParams.getModel(), field);
            Assert.isTrue(metaField.isEncrypted(),
                    "Field {0} of model {1} is not an encrypted field!", field, taskParams.getModel());
        }
    }

    /**
     * Execute the asynchronous task.
     * @param taskParams The parameters for executing the task.
     */
    @Override
    @SkipPermissionCheck
    public void execute(PlaintextToCiphertextParams taskParams) {
        // The fields an update has to carry to address a row, on top of the one being encrypted
        Set<String> keyFields = ModelManager.isTimelineModel(taskParams.getModel()) ?
                Sets.newHashSet(ID, SLICE_ID) : Sets.newHashSet(ID);
        // Construct the pagination query for reading dependent fields.
        Set<String> readFields = new HashSet<>(keyFields);
        readFields.addAll(taskParams.getFields());
        Filters filters = new Filters().in(ID, taskParams.getIds());
        FlexQuery flexQuery = new FlexQuery(readFields, filters).acrossTimelineData();
        // Get the original value from the database.
        flexQuery.setConvertType(ConvertType.ORIGINAL);
        List<Map<String, Object>> rows = modelService.searchList(taskParams.getModel(), flexQuery);
        // Verify the configured key before the first write, never in the middle of the task
        this.checkEncryptionKey(taskParams.getFields(), rows);
        taskParams.getFields().forEach(field -> {
            List<Map<String, Object>> plaintextRows = this.getPlaintextRows(keyFields, field, rows);
            if (!CollectionUtils.isEmpty(plaintextRows)) {
                modelService.updateList(taskParams.getModel(), plaintextRows);
            }
        });
    }

    /**
     * Decrypts one value that is already encrypted and discards the result. Encrypting the remaining rows
     * under a key the existing data was not written with would leave the column half readable, so a
     * mismatch has to stop the task before anything is written. One value answers it for the whole task -
     * a successful decryption is proof of the key - and deriving a key costs 10000 hash rounds.
     *
     * @param fields The fields whose historical data is being corrected.
     * @param rows The database rows read for those fields.
     */
    private void checkEncryptionKey(Set<String> fields, List<Map<String, Object>> rows) {
        for (String field : fields) {
            for (String value : ListUtils.extractValueIndexMap(rows, field).values()) {
                if (EncryptUtils.isCiphertext(value)) {
                    EncryptUtils.decrypt(value);
                    return;
                }
            }
        }
    }

    /**
     * Get the rows whose given field still holds a plaintext value, as an update carrying that field alone.
     *
     * @param keyFields The fields that address a row.
     * @param fieldName The name of the field that needs historical data correction.
     * @param rows The paginated database rows.
     * @return A list of updates for the rows whose specified field holds a plaintext value.
     */
    private List<Map<String, Object>> getPlaintextRows(Set<String> keyFields, String fieldName,
                                                       List<Map<String, Object>> rows) {
        // Extract a map of index-value, ignoring null and empty strings: a row whose field holds no value
        // has nothing to encrypt, and must not be rewritten.
        Map<Integer, String> valueMap = ListUtils.extractValueIndexMap(rows, fieldName);
        List<Map<String, Object>> plaintextRows = new ArrayList<>();
        // Telling plaintext from ciphertext is a test on the stored layout, so it costs no key derivation
        valueMap.forEach((index, value) -> {
            if (!EncryptUtils.isCiphertext(value)) {
                plaintextRows.add(this.toFieldUpdate(keyFields, fieldName, rows.get(index), value));
            }
        });
        return plaintextRows;
    }

    /**
     * Build the update for one row: the fields that address it, plus the single field being encrypted.
     * Submitting the whole row would carry the other encrypted fields' stored ciphertext back into the
     * write pipeline, which encrypts it a second time.
     *
     * @param keyFields The fields that address a row.
     * @param fieldName The name of the field being encrypted.
     * @param row The database row read with its original values.
     * @param value The plaintext value of the field being encrypted.
     * @return An update carrying the addressing fields and the field being encrypted.
     */
    private Map<String, Object> toFieldUpdate(Set<String> keyFields, String fieldName,
                                              Map<String, Object> row, String value) {
        Map<String, Object> update = new HashMap<>();
        keyFields.forEach(keyField -> update.put(keyField, row.get(keyField)));
        update.put(fieldName, value);
        return update;
    }
}
