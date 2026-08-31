package io.softa.starter.file.vo;

import java.io.Serial;
import java.time.LocalDate;
import java.io.Serializable;
import java.util.List;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;

/**
 * One workbook, one sheet per object.
 *
 * <p>An employee's addresses, family members and contacts are separate models, so exporting "an
 * employee and everything hanging off them" is several queries that have to arrive as one file. Each
 * object brings its own fields and its own filter — they share nothing but the workbook.
 *
 * <p>Every sheet should carry its object's {@code code}. That is the column an edited sheet is fed
 * back in by: a code names an existing row to update, and a blank one asks for a new one. Exported
 * without it, the file can be read but not returned.
 */
@Data
@Schema(name = "MultiSheetExportParams")
public class MultiSheetExportParams implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    @Schema(description = "Name of the generated file, without extension.")
    private String fileName;

    @Schema(description = "One entry per object; each becomes a sheet.")
    private List<Sheet> sheets;

    /**
     * The point in time every sheet is read at.
     *
     * <p>On the workbook rather than on each sheet, because it is not a per-sheet quantity: a record
     * and the objects under it read at different dates is not a picture of anything. It also cannot be
     * one — the effective date travels on the request context, not on the query, so several sheets
     * setting their own would leave whichever ran last in force for all of them.
     */
    @Schema(description = "Point in time all sheets are read at; applies to the whole workbook.")
    private LocalDate effectiveDate;

    /** One object's slice of the workbook. */
    @Data
    @Schema(name = "MultiSheetExportParams.Sheet")
    public static class Sheet implements Serializable {

        @Serial
        private static final long serialVersionUID = 1L;

        @Schema(description = "The model to export.")
        private String modelName;

        /**
         * Tab name. Blank takes the model name — which is what "one sheet per object" means, so the
         * caller only names a sheet when it wants something other than the obvious.
         */
        @Schema(description = "Tab name; defaults to the model name.")
        private String sheetName;

        @Schema(description = "Fields, filters and ordering for this object.")
        private ExportParams exportParams;
    }
}
