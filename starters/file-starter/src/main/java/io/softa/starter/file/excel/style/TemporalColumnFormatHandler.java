package io.softa.starter.file.excel.style;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;

import io.softa.framework.base.constant.TimeConstant;
import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

/**
 * Gives an import template's date, datetime and time columns the format the instructions ask for,
 * instead of leaving it to whichever locale opens the file.
 *
 * <p>A template is a header row and nothing else, so its columns are {@code General}. Excel then
 * decides what a typed date looks like: {@code 2024/9/15} on a Chinese machine, {@code 9/15/2024} on
 * an American one, while the instruction sheet beside it asks for {@code yyyy-MM-dd}. The value
 * imports either way — {@code DateUtils} normalises separators — but the template contradicts itself
 * on screen, which is what was reported.
 *
 * <p>Only the column default, and only the template. Exported files were never affected: the writer
 * already renders a {@code LocalDate} as {@code yyyy-MM-dd} and a {@code LocalDateTime} as
 * {@code yyyy-MM-dd HH:mm:ss} of its own accord, which {@code TemporalColumnFormatWorkbookTest}
 * pins so that a change there is noticed rather than assumed. A written cell needs nothing from
 * here; an empty column has nothing else.
 */
public class TemporalColumnFormatHandler implements SheetWriteHandler {

    /** Column index → the Excel number format its field type asks for. */
    private final Map<Integer, String> formatByColumn;

    /** One reusable style per format, per workbook: a workbook caps at 64k styles. */
    private final Map<String, CellStyle> styleByFormat = new LinkedHashMap<>();

    private TemporalColumnFormatHandler(Map<Integer, String> formatByColumn) {
        this.formatByColumn = formatByColumn;
    }

    /**
     * A handler for the temporal columns among {@code fieldNames}, or null when there are none —
     * the caller passes that straight to the sheet builder, which ignores nulls.
     *
     * <p>{@code fieldNames} is positional: index i names what column i holds, dotted paths included
     * ({@code employeeProfileId.dateOfBirth}), resolved the same way the exporter resolves its
     * headers. A name that resolves to nothing simply has no format to apply — a template may carry
     * columns that are not fields at all.
     */
    public static TemporalColumnFormatHandler forFields(String modelName, List<String> fieldNames) {
        if (modelName == null || fieldNames == null) {
            return null;
        }
        Map<Integer, String> formats = new LinkedHashMap<>();
        for (int i = 0; i < fieldNames.size(); i++) {
            String format = formatOf(modelName, fieldNames.get(i));
            if (format != null) {
                formats.put(i, format);
            }
        }
        return formats.isEmpty() ? null : new TemporalColumnFormatHandler(formats);
    }

    private static String formatOf(String modelName, String fieldName) {
        MetaField metaField;
        try {
            metaField = ModelManager.getLastFieldOfCascaded(modelName, fieldName);
        } catch (RuntimeException e) {
            // Not a field of this model — nothing to format, and not a reason to fail the download.
            return null;
        }
        if (metaField == null || metaField.getFieldType() == null) {
            return null;
        }
        return switch (metaField.getFieldType()) {
            case DATE -> TimeConstant.DATE_FORMAT;
            case DATE_TIME -> TimeConstant.DATETIME_FORMAT;
            case TIME -> TimeConstant.TIME_FORMAT;
            default -> null;
        };
    }

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        Sheet sheet = context.getWriteSheetHolder().getSheet();
        Workbook workbook = sheet.getWorkbook();
        formatByColumn.forEach((columnIndex, format) ->
                sheet.setDefaultColumnStyle(columnIndex, styleFor(workbook, format)));
    }


    private CellStyle styleFor(Workbook workbook, String format) {
        return styleByFormat.computeIfAbsent(format, f -> {
            CellStyle style = workbook.createCellStyle();
            style.setDataFormat(workbook.createDataFormat().getFormat(f));
            return style;
        });
    }

}
