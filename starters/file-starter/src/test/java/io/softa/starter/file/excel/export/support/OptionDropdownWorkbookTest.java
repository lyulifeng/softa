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
            assertThat(validations)
                    .extracting(v -> v.getRegions().getCellRangeAddress(0).formatAsString(),
                            v -> v.getValidationConstraint().getFormula1())
                    .containsExactlyInAnyOrder(
                            org.assertj.core.groups.Tuple.tuple("B2:B5001", "\"Father,Mother,Spouse,Child,Others\""),
                            org.assertj.core.groups.Tuple.tuple("E2:E5001", "\"Male,Female,Other\""));

            // The prose sheet beside it is left alone.
            assertThat(workbook.getSheetAt(1).getDataValidations()).isEmpty();
        }

        // The arrow is asserted on the raw XML, not through POI's getter, because the getter reports
        // POI's own naming and both layers invert: POI writes suppress=true as showDropDown="false",
        // and Excel treats showDropDown="true" as "no in-cell dropdown". Only the attribute Excel
        // actually reads says whether a reader will see the list, and asserting through the getter is
        // what let a build ship with every validation in place and every arrow hidden.
        assertThat(sheetXml(bytes)).contains("showDropDown=\"false\"").doesNotContain("showDropDown=\"true\"");
    }

    /** The first worksheet's XML, straight out of the .xlsx container. */
    private String sheetXml(byte[] bytes) throws Exception {
        // Via a file rather than a stream: the writer emits entries with data descriptors, which
        // ZipInputStream refuses to read back.
        java.nio.file.Path file = java.nio.file.Files.createTempFile("dropdown", ".xlsx");
        try {
            java.nio.file.Files.write(file, bytes);
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(file.toFile())) {
                java.util.zip.ZipEntry entry = zip.getEntry("xl/worksheets/sheet1.xml");
                assertThat(entry).as("sheet1.xml in the workbook").isNotNull();
                try (java.io.InputStream in = zip.getInputStream(entry)) {
                    return new String(in.readAllBytes(), java.nio.charset.StandardCharsets.UTF_8);
                }
            }
        } finally {
            java.nio.file.Files.deleteIfExists(file);
        }
    }
}
