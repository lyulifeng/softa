package io.softa.framework.web.service.impl;

import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import com.google.common.collect.Sets;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.util.CollectionUtils;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.security.EncryptUtils;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.ListUtils;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Page;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.service.ToolkitService;
import io.softa.framework.web.utils.RecomputeUtils;

/**
 * The implementation class for ToolkitService
 */
@Slf4j
@Service
public class ToolkitServiceImpl implements ToolkitService {

    @Autowired
    private ModelService<?> modelService;

    /**
     * Recompute the stored calculation fields, including computed and cascaded fields.
     *
     * @param modelName model name
     * @param fields fields to be recomputed
     */
    @Override
    @SkipPermissionCheck
    public void recompute(String modelName, Set<String> fields) {
        // TODO: Asynchronous task processing
        // Get the dependent fields for stored cascaded and computed fields
        Set<String> dependedFields = RecomputeUtils.getDependedFields(modelName, fields);
        Assert.notEmpty(dependedFields, "No stored cascaded or computed fields need recalculation for model {0}!", modelName);
        dependedFields.addAll(ModelManager.isTimelineModel(modelName) ?
                Sets.newHashSet(ModelConstant.ID, ModelConstant.SLICE_ID) : Sets.newHashSet(ModelConstant.ID));
        // Construct FlexQuery to read dependent fields for pagination
        FlexQuery flexQuery = new FlexQuery(dependedFields).acrossTimelineData();
        Page<Map<String, Object>> page = Page.ofCursorPage(BaseConstant.DEFAULT_BATCH_SIZE);
        // Paginate requests for dependent fields and trigger updates by batch processing each page
        do {
            page = modelService.searchPage(modelName, flexQuery, page);
            // TODO: When both the main model and the cascaded model are timeline models, the calculation of
            //  cascaded data can only proceed after getting the `effectiveStartDate` from the main model data,
            //  which is then used as the `effectiveDate` to fetch data from the cascaded model.
            if (!page.getRows().isEmpty()) {
                modelService.updateList(modelName, page.getRows());
            }
        } while (page.toNext());
    }

    /**
     * Encrypts historical plaintext data after the field is set to `encrypted=true`.
     *
     * @param modelName model name
     * @param fieldName field to encrypt historical plaintext data.
     * @param dryRun true to report the rows that would be encrypted, without writing them
     * @return the number of rows fixed, or that a dry run would fix
     */
    @Override
    @SkipPermissionCheck
    public Long fixUnencryptedData(String modelName, String fieldName, boolean dryRun) {
        // TODO: Asynchronous task processing
        MetaField metaField = ModelManager.getModelField(modelName, fieldName);
        Assert.isTrue(metaField.isEncrypted(), "The field {0} of model {1} is not an encrypted field!", fieldName, modelName);
        long fixedCount = 0L;
        // Construct query to read required fields for pagination
        Set<String> readFields = ModelManager.isTimelineModel(modelName) ?
                Sets.newHashSet(ModelConstant.ID, ModelConstant.SLICE_ID) : Sets.newHashSet(ModelConstant.ID);
        readFields.add(fieldName);
        FlexQuery flexQuery = new FlexQuery(readFields).acrossTimelineData();
        // Get the original data from database without expansion or conversion.
        flexQuery.setConvertType(ConvertType.ORIGINAL);
        Page<Map<String, Object>> page = Page.ofCursorPage(BaseConstant.DEFAULT_BATCH_SIZE);
        // The configured key is checked once for the whole scan, against the first encrypted value met
        AtomicBoolean keyChecked = new AtomicBoolean();
        // Paginate requests for data and process each page
        do {
            page = modelService.searchPage(modelName, flexQuery, page);
            if (!page.getRows().isEmpty()) {
                fixedCount += this.fixPlaintextRows(modelName, fieldName, page.getRows(), dryRun, keyChecked);
            }
        } while (page.toNext());
        log.info("Model field {}: {} - {} row(s) hold a plaintext value{}", modelName, fieldName, fixedCount,
                dryRun ? ", nothing was written (dry run)" : " and were encrypted");
        return fixedCount;
    }

    /**
     * Splits the original database values into the ones already encrypted and the ones still in plaintext,
     * then invokes update method to encrypt the plaintext ones.
     *
     * @param modelName the name of the model
     * @param fieldName the field name of the historical data to be fixed
     * @param rows the database rows obtained through pagination
     * @param dryRun true to count the rows that would be encrypted, without writing them
     * @param keyChecked whether the configured key has already been verified earlier in the scan
     * @return the number of historical rows fixed in the current page
     */
    private Integer fixPlaintextRows(String modelName, String fieldName, List<Map<String, Object>> rows,
                                     boolean dryRun, AtomicBoolean keyChecked) {
        // Extract a map of index-value, ignoring null and empty strings: a row whose field holds no value
        // is neither encrypted nor in need of it, and must not be counted or rewritten.
        Map<Integer, String> valueMap = ListUtils.extractValueIndexMap(rows, fieldName);
        // Telling the two apart is a test on the stored layout, so it costs no key derivation
        List<Map<String, Object>> plaintextRows = new ArrayList<>();
        String keyProbe = null;
        for (Map.Entry<Integer, String> entry : valueMap.entrySet()) {
            if (EncryptUtils.isCiphertext(entry.getValue())) {
                if (keyProbe == null) {
                    keyProbe = entry.getValue();
                }
            } else {
                plaintextRows.add(rows.get(entry.getKey()));
            }
        }
        // Decrypt one value that is already encrypted and discard the result: this is a key check.
        // Encrypting the rest under a key the existing data was not written with would leave the column
        // half readable, so a mismatch has to stop the run before anything is written, and a dry run
        // makes the same check. One value answers it for the whole scan - a successful decryption is
        // proof of the key - and deriving a key costs 10000 hash rounds, so this is not done per row.
        if (keyProbe != null && keyChecked.compareAndSet(false, true)) {
            EncryptUtils.decrypt(keyProbe);
        }
        if (CollectionUtils.isEmpty(plaintextRows)) {
            return 0;
        }
        if (!dryRun) {
            // Update the plaintext data to trigger encryption storage
            modelService.updateList(modelName, plaintextRows);
        }
        return plaintextRows.size();
    }

}
