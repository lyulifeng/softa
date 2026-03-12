package io.softa.framework.orm.jdbc.pipeline.processor;

import java.util.List;
import java.util.Map;
import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.MetaField;

/**
 * Filter field processor
 */
public class FiltersProcessor extends BaseProcessor {

    public FiltersProcessor(MetaField metaField, AccessType accessType) {
        super(metaField, accessType);
    }

    /**
     * Convert the Filters object to a string and store it in the database.
     *
     * @param row Single-row data to be created/updated
     */
    @Override
    public void processInputRow(Map<String, Object> row) {
        boolean isContain = row.containsKey(fieldName);
        checkReadonly(isContain);
        Object value = row.get(fieldName);
        if (value instanceof Filters) {
            row.put(fieldName,  value.toString());
        } else if (value instanceof String) {
            row.put(fieldName, value);
        } else if (value instanceof List<?> listValue) {
            row.put(fieldName, JsonUtils.objectToString(listValue));
        } else if (AccessType.CREATE.equals(accessType)) {
            checkNotBlank(value);
            row.computeIfAbsent(fieldName, k -> metaField.getDefaultValueObject());
        }
    }

    /**
     * Convert the string value to a Filters object and replace the original value.
     *
     * @param row Single-row data to be read
     */
    @Override
    public void processOutputRow(Map<String, Object> row) {
        if (!row.containsKey(fieldName)) {
            return;
        }
        Object value = row.get(fieldName);
        value = StringUtils.isBlank((String) value) ? new Filters() : Filters.of((String) value);
        row.put(fieldName, value);
    }

}
