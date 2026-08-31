package io.softa.starter.file.controller;

import java.time.LocalDate;
import java.util.List;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.file.excel.export.support.ExcelUploadService;
import io.softa.starter.file.vo.ExportParams;
import io.softa.starter.file.vo.MultiSheetExportParams;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * One workbook carrying several objects — an employee's addresses, family members and contacts are
 * separate models, and "this employee and everything under them" is one file rather than three
 * downloads.
 *
 * <p>The service that writes such a workbook existed and had no caller: nothing in the product could
 * reach it, because no endpoint exposed it. These cover the two things that broke the moment it got
 * one.
 */
class MultiSheetExportTest {

    @Test
    void aSheetNameIsCutToWhatExcelWillActuallyStore() {
        // Excel caps a sheet name at 31 characters. A longer one is altered on the way in, so anything
        // that later looks the sheet up by the name it asked for misses — the workbook ends up with
        // the data written and nothing attached to it. That exact failure cost the import templates
        // their dropdowns once already.
        String tooLong = "Employee Professional Qualification";
        assertThat(tooLong.length()).isGreaterThan(31);

        assertThat(ExcelUploadService.safeSheetName(tooLong))
                .hasSize(31)
                .isEqualTo(tooLong.substring(0, 31));
    }

    @Test
    void theCharactersExcelRefusesAreReplacedRatherThanRejected() {
        // []:*?/\ are illegal in a sheet name. A model label is free text and can hold any of them.
        assertThat(ExcelUploadService.safeSheetName("Pay / Benefits [2026]"))
                .doesNotContain("/").doesNotContain("[").doesNotContain("]");
    }

    @Test
    void aBlankNameIsLeftForTheWriterToFillFromTheModel() {
        // The writer substitutes the model name. Making one up here would take that choice away, and
        // "" is not a name Excel can be handed either.
        assertThat(ExcelUploadService.safeSheetName(null)).isNull();
        assertThat(ExcelUploadService.safeSheetName("")).isEmpty();
    }

    @Test
    void theRequestCarriesEachObjectsOwnFieldsAndFilter() {
        // The objects share nothing but the workbook: different models, different columns, different
        // rows. A single field list across all of them would describe none of them.
        MultiSheetExportParams params = new MultiSheetExportParams();
        params.setFileName("Employee 360");
        params.setSheets(List.of(
                sheet("EmpAddress", List.of("code", "addressType", "postalCode")),
                sheet("EmpFamilyMember", List.of("code", "relationship", "name"))));

        assertThat(params.getSheets()).hasSize(2);
        assertThat(params.getSheets().getFirst().getExportParams().getFields())
                .as("every sheet leads with the code an edited file is fed back in by")
                .startsWith("code");
        assertThat(params.getSheets().get(1).getExportParams().getFields())
                .containsExactly("code", "relationship", "name");
    }

    @Test
    void theEffectiveDateSitsOnTheWorkbookBecauseItCannotSitOnASheet() {
        // It travels on the request context, not on the query, and every sheet is converted before any
        // of them is read. A per-sheet value would therefore be overwritten by the next sheet and only
        // the last one would take effect — for all of them. Worse in practice than in theory: a caller
        // that sets a date on the first sheet and none on the rest ends up with it cleared.
        MultiSheetExportParams params = new MultiSheetExportParams();
        params.setEffectiveDate(LocalDate.of(2026, 8, 25));

        assertThat(params.getEffectiveDate()).isEqualTo(LocalDate.of(2026, 8, 25));
        assertThat(MultiSheetExportParams.Sheet.class.getDeclaredFields())
                .as("a sheet carries no date of its own to be confused with the workbook's")
                .noneMatch(field -> "effectiveDate".equals(field.getName()));
    }

    private static MultiSheetExportParams.Sheet sheet(String modelName, List<String> fields) {
        ExportParams exportParams = new ExportParams();
        exportParams.setFields(fields);
        exportParams.setFilters(new Filters());
        MultiSheetExportParams.Sheet sheet = new MultiSheetExportParams.Sheet();
        sheet.setModelName(modelName);
        sheet.setExportParams(exportParams);
        return sheet;
    }
}
