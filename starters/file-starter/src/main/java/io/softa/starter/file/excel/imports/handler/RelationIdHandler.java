package io.softa.starter.file.excel.imports.handler;

import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.exception.ValidationException;
import io.softa.framework.orm.meta.MetaField;
import io.softa.starter.file.dto.ImportFieldDTO;

/**
 * Guards a relation column mapped by its bare field name — i.e. one that imports the related row's
 * <b>id</b> rather than a business key.
 *
 * <p>This is a legitimate mapping (the id may be what the source system exports), but it is also the
 * shape a template gets wrong most easily, and the failure used to be unreadable: the cell text went
 * straight to the write and a numeric FK produced the JDK's {@code For input string: "Branch / Branch"}.
 * The value in that report was not even a wrong guess — it is exactly what a detail page shows for the
 * row, because {@code displayName} joins the related row's fields with {@code " / "}.
 *
 * <p>So the message names the column, says an id is expected, and points at the alternative that is
 * almost always what the author meant: map the column to a dotted path
 * ({@code orgType.itemCode}) and the business key is resolved for them.
 *
 * <p>Only applies when the related id is numeric. A code-as-id master ({@code CountryRegion},
 * {@code Currency}) has a String id which IS the portable code, so a bare column there is both correct
 * and readable — those keep the default handler.
 */
public class RelationIdHandler extends BaseImportHandler {

    private final String relatedModel;
    private final String lookupHint;

    public RelationIdHandler(MetaField metaField, ImportFieldDTO importFieldDTO, String lookupHint) {
        super(metaField, importFieldDTO);
        this.relatedModel = metaField.getRelatedModel();
        this.lookupHint = lookupHint;
    }

    @Override
    public Object handleValue(Object value) {
        if (!(value instanceof String valueStr) || StringUtils.isBlank(valueStr)) {
            return value;
        }
        String trimmed = valueStr.trim();
        try {
            return Long.valueOf(trimmed);
        } catch (NumberFormatException e) {
            throw new ValidationException(
                    "The field `{0}` expects a {1} id, but got `{2}`. Fill the numeric id, or import by "
                            + "business key instead — map this column to `{3}`.",
                    label, relatedModel, trimmed, lookupHint);
        }
    }
}
