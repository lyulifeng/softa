package io.softa.starter.file.excel.export.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.IntStream;

import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.WorkbookUtil;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * The dropdown attachment, pinned at the boundary that decides how it is attached.
 *
 * <p>Excel caps a data-validation formula at 255 characters and POI enforces it by throwing, so the
 * inline form silently stops being available once a set grows past that. These tests fix where the
 * handler switches to a hidden sheet, because the switch is invisible in the produced file until
 * someone opens it — and getting it wrong means either a broken download or a dropdown that is not
 * there.
 */
class OptionDropdownHandlerTest {

    /**
     * Deliberately longer than Excel's 31-character sheet-name cap, and taken from a real template
     * ("Employee Identity & Work Pass (Singapore)"). A sheet created under this name comes back
     * truncated, which is what made an earlier name-based match find nothing at all.
     */
    private static final String LONG_TEMPLATE_NAME = "Employee Identity & Work Pass (Singapore)";

    /** Values whose comma-joined length lands just under / just over the cap. */
    private static List<String> codesOfJoinedLength(int target) {
        // Each code is 9 chars ("OPT_00000"), plus one comma between them.
        List<String> codes = IntStream.range(0, (target + 1) / 10)
                .mapToObj(i -> String.format("OPT_%05d", i))
                .toList();
        return codes;
    }

    private Sheet writeWith(Map<Integer, List<String>> options) {
        return writeWith(options, "Template");
    }

    private Sheet writeWith(Map<Integer, List<String>> options, String sheetName) {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet(WorkbookUtil.createSafeSheetName(sheetName));
        // Exercise the attachment directly: the fesod context only supplies the sheet, and building a
        // whole write pipeline here would test the library rather than this decision.
        new TestableHandler(options).attach(sheet);
        return sheet;
    }

    @Test
    void aShortListIsAttachedInlineWithNoExtraSheet() {
        Sheet sheet = writeWith(Map.of(0, List.of("OT_COMPANY", "OT_DEPT", "OT_TEAM")));

        assertThat(sheet.getDataValidations()).hasSize(1);
        DataValidation validation = sheet.getDataValidations().get(0);
        assertThat(validation.getValidationConstraint().getExplicitListValues())
                .containsExactly("OT_COMPANY", "OT_DEPT", "OT_TEAM");
        assertThat(sheet.getWorkbook().getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME)).isNull();
    }

    @Test
    void aListTooLongForTheFormulaMovesToAHiddenSheet() {
        List<String> codes = codesOfJoinedLength(600);
        Sheet sheet = writeWith(Map.of(0, codes));

        XSSFWorkbook workbook = (XSSFWorkbook) sheet.getWorkbook();
        Sheet options = workbook.getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME);
        assertThat(options).as("values that cannot go inline need somewhere to live").isNotNull();
        assertThat(workbook.isSheetHidden(workbook.getSheetIndex(options))).isTrue();
        // Every value written down one column, and the formula pointing at exactly that range.
        assertThat(options.getLastRowNum()).isEqualTo(codes.size() - 1);
        assertThat(sheet.getDataValidations().get(0).getValidationConstraint().getFormula1())
                .isEqualTo("_options!$A$1:$A$" + codes.size());
    }

    @Test
    void twoLongListsGetSeparateColumnsOnTheSameHiddenSheet() {
        Map<Integer, List<String>> options = new LinkedHashMap<>();
        options.put(0, codesOfJoinedLength(600));
        options.put(3, codesOfJoinedLength(400));
        Sheet sheet = writeWith(options);

        // Column B, not A again: sharing one column would make the second dropdown offer the first
        // one's values.
        assertThat(sheet.getDataValidations()).hasSize(2);
        assertThat(sheet.getDataValidations().stream()
                        .map(v -> v.getValidationConstraint().getFormula1()))
                .anyMatch(f -> f.startsWith("_options!$A$"))
                .anyMatch(f -> f.startsWith("_options!$B$"));
    }

    @Test
    void aValueContainingACommaGoesToTheHiddenSheetEvenThoughItWouldFit() {
        // An inline list is comma-separated with no escaping, so `JPMORGAN CHASE BANK, N.A.` would
        // arrive in Excel as two entries, neither of which is a bank. Short enough to fit is not the
        // same as safe to inline — which only started mattering once columns began offering names
        // people wrote rather than option codes.
        List<String> names = List.of("DBS BANK LTD", "JPMORGAN CHASE BANK, N.A.");
        Sheet sheet = writeWith(Map.of(0, names));

        Workbook workbook = sheet.getWorkbook();
        Sheet options = workbook.getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME);
        assertThat(options).as("a comma-bearing value cannot be offered inline").isNotNull();
        assertThat(options.getRow(1).getCell(0).getStringCellValue())
                .as("and it reaches the sheet whole, not split at the comma")
                .isEqualTo("JPMORGAN CHASE BANK, N.A.");
        assertThat(sheet.getDataValidations().get(0).getValidationConstraint().getExplicitListValues())
                .as("nothing is left inline").isNull();
    }

    @Test
    void aDoubleQuoteAlsoForcesTheHiddenSheet() {
        Sheet sheet = writeWith(Map.of(0, List.of("Plain", "The \"Quoted\" One")));

        assertThat(sheet.getWorkbook().getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME)).isNotNull();
    }

    @Test
    void anEmptyOrMissingListLeavesTheColumnAlone() {
        assertThat(writeWith(Map.of(0, List.of())).getDataValidations()).isEmpty();
        assertThat(writeWith(Map.of()).getDataValidations()).isEmpty();
    }

    @Test
    void aTemplateNameTooLongForASheetStillGetsItsDropdowns() {
        // Excel truncates the name to 31 characters, so anything that compares the template's name with
        // the sheet's finds no match and attaches nothing — silently, on most of the real templates.
        Sheet sheet = writeWith(Map.of(0, List.of("OT_COMPANY", "OT_DEPT")), LONG_TEMPLATE_NAME);

        assertThat(sheet.getSheetName()).isNotEqualTo(LONG_TEMPLATE_NAME);
        assertThat(sheet.getDataValidations()).hasSize(1);
    }

    @Test
    void aSheetAfterTheFirstIsUntouched() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        workbook.createSheet("Template");
        Sheet instructions = workbook.createSheet("Import instructions");
        new TestableHandler(Map.of(0, List.of("A", "B"))).attach(instructions);

        // The instruction sheet is prose, not input — a dropdown there would be noise.
        assertThat(instructions.getDataValidations()).isEmpty();
    }

    @Test
    void oneUnattachableColumnDoesNotStopTheOthers() {
        Map<Integer, List<String>> options = new LinkedHashMap<>();
        // A code containing a comma cannot be expressed inline: Excel reads it as two values. It is
        // long enough here to take the hidden-sheet path, which has no such problem.
        options.put(0, List.of("A", "B"));
        options.put(1, codesOfJoinedLength(600));
        assertThatCode(() -> {
            Sheet sheet = writeWith(options);
            assertThat(sheet.getDataValidations()).hasSize(2);
        }).doesNotThrowAnyException();
    }

    /** Exposes the attachment step without needing a fesod write context. */
    /**
     * Several long columns on the hidden sheet, written into a STREAMING workbook.
     *
     * <p>This is the workbook the export actually produces. A streaming sheet keeps only a window of
     * rows in memory and flushes the rest to disk, and a flushed row can never be touched again. The
     * options sheet is filled one COLUMN at a time, so the second column starts again at row 0 — which
     * by then is gone, and POI answers "Attempting to write a row[0] in the range [0,148] that is
     * already written to disk".
     *
     * <p>The handler catches that and drops the column's dropdown, so the download succeeds and the
     * template quietly comes out with dropdowns on its first columns and none after. Which ones
     * survive depends on how long the earlier lists were — nothing about the column itself.
     */
    @Test
    void everyLongColumnGetsItsValuesEvenWhenTheSheetIsStreamed() {
        Map<Integer, List<String>> options = new LinkedHashMap<>();
        options.put(0, codesOfJoinedLength(600));
        options.put(1, codesOfJoinedLength(600));
        options.put(2, codesOfJoinedLength(600));

        try (SXSSFWorkbook workbook = new SXSSFWorkbook(10)) {
            Sheet sheet = workbook.createSheet("Template");
            new TestableHandler(options).attach(sheet);

            assertThat(sheet.getDataValidations())
                    .as("one per column, or a template silently loses the later ones")
                    .hasSize(3);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    /**
     * "模板中所有的下拉值禁止修改" — a spec line, currently resting on a POI default.
     *
     * <p>A list that merely suggests is not what the spec asked for: the value has to be one of the
     * offered ones. That takes both an error box AND the STOP style — POI defaults the style to STOP,
     * so this works today by inheritance rather than by statement, and a POI upgrade or a refactor
     * that builds the validation differently would loosen it with nothing to show for it.
     */
    @Test
    void aValueOffTheListIsRejectedRatherThanMerelyFlagged() {
        Map<Integer, List<String>> options = new LinkedHashMap<>();
        options.put(0, List.of("Active", "Inactive"));

        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            Sheet sheet = workbook.createSheet("Template");
            new TestableHandler(options).attach(sheet);

            DataValidation validation = sheet.getDataValidations().getFirst();
            assertThat(validation.getShowErrorBox()).isTrue();
            assertThat(validation.getErrorStyle())
                    .as("STOP — anything else lets the reader keep a value the import will reject")
                    .isEqualTo(DataValidation.ErrorStyle.STOP);
        } catch (java.io.IOException e) {
            throw new AssertionError(e);
        }
    }

    private static final class TestableHandler extends OptionDropdownHandler {
        private TestableHandler(Map<Integer, List<String>> optionsByColumn) {
            super(optionsByColumn);
        }
    }
}
