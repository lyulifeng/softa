package io.softa.starter.file.excel.export.support;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The handler driven the way production drives it — through the writer, not by hand.
 *
 * <p>{@link OptionDropdownHandlerTest} calls {@code attach} on a workbook it built itself, which
 * proves the validations are correct but not that anything ever calls it. A handler that overrode a
 * lifecycle method the writer does not invoke would satisfy every one of those tests and still put no
 * dropdown in the downloaded file. So this one writes a real workbook, with the two sheets and the
 * long template name an import template actually has, and reads the bytes back.
 */
class OptionDropdownWorkbookTest {

    private static final String TEMPLATE_NAME = "Employee Family Members Import Template";

    private byte[] writeTemplate(Map<Integer, List<String>> options) throws Exception {
        List<List<String>> heads = List.of(
                List.of("Employee Code"), List.of("Relationship"), List.of("Name"),
                List.of("Date of Birth"), List.of("Gender"));
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(out).build()) {
            WriteSheet main = FesodSheet.writerSheet(0, TEMPLATE_NAME).head(heads)
                    .registerWriteHandler(new OptionDropdownHandler(options)).build();
            writer.write(Collections.emptyList(), main);
            WriteSheet instructions = FesodSheet.writerSheet(1, "Import instructions").head(heads).build();
            writer.write(Collections.emptyList(), instructions);
            writer.finish();
        }
        return out.toByteArray();
    }

    @Test
    void theWrittenWorkbookCarriesADropdownOnEveryOptionColumn() throws Exception {
        byte[] bytes = writeTemplate(Map.of(
                1, List.of("Father", "Mother", "Spouse", "Child", "Others"),
                4, List.of("Male", "Female", "Other")));

        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(bytes))) {
            // The name Excel actually stored, truncated to its 31-character limit — the reason
            // matching the sheet by name could not work.
            assertThat(workbook.getSheetAt(0).getSheetName()).hasSize(31);

            List<? extends DataValidation> validations = workbook.getSheetAt(0).getDataValidations();
            assertThat(validations).hasSize(2);
            assertThat(validations).allSatisfy(v -> assertThat(v.getSuppressDropDownArrow()).isFalse());
            assertThat(validations)
                    .extracting(v -> v.getRegions().getCellRangeAddress(0).formatAsString(),
                            v -> v.getValidationConstraint().getFormula1())
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("B2:B5001", "\"Father,Mother,Spouse,Child,Others\""),
                            org.assertj.core.groups.Tuple.tuple("E2:E5001", "\"Male,Female,Other\""));

            // The prose sheet beside it is left alone.
            assertThat(workbook.getSheetAt(1).getDataValidations()).isEmpty();
        }
    }

    @Test
    void aTemplateWithNoOptionColumnsWritesAPlainWorkbook() throws Exception {
        try (XSSFWorkbook workbook = new XSSFWorkbook(new ByteArrayInputStream(writeTemplate(Map.of())))) {
            assertThat(workbook.getNumberOfSheets()).isEqualTo(2);
            assertThat(workbook.getSheetAt(0).getDataValidations()).isEmpty();
            // No hidden sheet appears when nothing needed one.
            assertThat(workbook.getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME)).isNull();
        }
    }
}
