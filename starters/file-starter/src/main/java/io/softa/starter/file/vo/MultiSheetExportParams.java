package io.softa.starter.file.vo;

import java.io.Serial;
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
