package io.softa.starter.file.excel.imports.handler;

import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.exception.ValidationException;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.MetaOptionItem;
import io.softa.framework.orm.meta.OptionManager;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * BooleanHandler
 *
 * <p>Accepts either side of the {@code BooleanValue} option set — the stored {@code itemCode}
 * ({@code true} / {@code false}) or the label a person actually sees and exports ({@code Yes} /
 * {@code No}), in any casing. A spreadsheet is filled by hand and read back from an export, so
 * insisting on one spelling of a yes/no cell fails rows that are not wrong.
 */
public class BooleanHandler extends BaseImportHandler {

    public BooleanHandler(MetaField metaField, ImportFieldDTO importFieldDTO) {
        super(metaField, importFieldDTO);
    }

    /**
     * Handle the Object value
     * @param value The Object value
     * @return The Boolean value
     */
    public Object handleValue(Object value) {
        if (value instanceof String valueStr && StringUtils.isNotBlank(valueStr)) {
            String raw = valueStr.trim();
            String optionSetCode = BaseConstant.BOOLEAN_OPTION_SET_CODE;
            // itemCode first, lower-cased: the stored codes are `true` / `false`, so this also
            // takes TRUE / True / true.
            String lower = raw.toLowerCase();
            if (OptionManager.existsItemCode(optionSetCode, lower)) {
                return Boolean.valueOf(lower);
            }
            // Then the label. Matched case-insensitively against the option set rather than
            // against a lower-cased copy of the input: the labels are `Yes` / `No`, so comparing
            // a lower-cased cell to them never matched and the compatibility this class advertises
            // never worked — every exported Yes/No column failed to come back in.
            String optionItemCode = itemCodeByLabelIgnoreCase(optionSetCode, raw);
            if (optionItemCode == null) {
                throw new ValidationException(
                        "The Boolean field `{0}` is incorrect `{1}`. Use `true` / `false`, or the "
                                + "labels `Yes` / `No`.", label, raw);
            }
            return Boolean.valueOf(optionItemCode);
        } else {
            return value;
        }
    }

    /** The itemCode whose label equals {@code label}, ignoring case; null when none does. */
    private static String itemCodeByLabelIgnoreCase(String optionSetCode, String label) {
        for (MetaOptionItem item : OptionManager.getMetaOptionItems(optionSetCode)) {
            if (label.equalsIgnoreCase(item.getLabel())) {
                return item.getItemCode();
            }
        }
        return null;
    }

}
