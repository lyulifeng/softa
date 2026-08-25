package io.softa.starter.file.excel.export.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFSheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The narrowed column: education tracks offered for the level already chosen, rather than every track
 * in the country.
 *
 * <p>Assertions read the worksheet XML, not POI's own getters. The two disagree about more than one
 * attribute — {@code suppressDropDownArrow} is written through inverted — and an assertion that shares
 * an API with the code under test cannot catch that code using it wrongly. This exact blind spot let a
 * change ship in 2026-08 that silently disabled every dropdown while four layers of tests stayed green.
 */
class OptionDropdownCascadeTest {

    private static final Map<String, List<String>> TRACKS_BY_LEVEL = new LinkedHashMap<>(Map.of());

    static {
        TRACKS_BY_LEVEL.put("SG_Bachelor", List.of("SG_BEng", "SG_BSc"));
        TRACKS_BY_LEVEL.put("SG_Master", List.of("SG_MEng"));
    }

    /** Column 3 holds the level, column 4 the track narrowed by it. */
    private Sheet write() {
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Template");
        Map<Integer, List<String>> options = new LinkedHashMap<>();
        options.put(3, List.of("SG_Bachelor", "SG_Master"));
        options.put(4, List.of("SG_BEng", "SG_BSc", "SG_MEng"));
        Map<Integer, OptionDropdownResolver.Cascade> cascades =
                Map.of(4, new OptionDropdownResolver.Cascade(3, TRACKS_BY_LEVEL));
        new TestableCascadeHandler(options, cascades).attach(sheet);
        return sheet;
    }

    @Test
    void theChildColumnPointsAtTheNameTheParentsPositionSelects() {
        Sheet sheet = write();
        String xml = ((XSSFSheet) sheet).getCTWorksheet().toString();

        // MATCH on the parent cell, absolute column and relative row so the rule travels down; IFERROR
        // so a row whose level is still blank resolves to _c0 rather than to nothing at all.
        // `&` arrives XML-escaped; asserting on the raw text is the point — POI's own getter would
        // hand back its idea of the formula rather than what the file carries.
        assertThat(xml)
                .contains("INDIRECT(\"_c4_\"&amp;IFERROR(MATCH($D2,_options!$A$1:$A$2,0),0))")
                .as("the arrow has to be on, and POI writes this attribute inverted")
                .contains("showDropDown=\"false\"");
    }

    @Test
    void eachParentGetsItsOwnNumberedNameHoldingOnlyItsChildren() {
        Sheet sheet = write();
        XSSFWorkbook workbook = (XSSFWorkbook) sheet.getWorkbook();

        // _c4_1 is the first parent's children, _c4_2 the second's — numbered by position, never
        // derived from the parent's text, which Excel would reject as a defined name. The 4 is the
        // column: a defined name belongs to the workbook, so two cascaded columns would otherwise
        // fight over the same names.
        Name first = workbook.getName("_c4_1");
        Name second = workbook.getName("_c4_2");
        assertThat(first).isNotNull();
        assertThat(second).isNotNull();
        assertThat(first.getRefersToFormula()).isNotEqualTo(second.getRefersToFormula());

        Sheet options = workbook.getSheet(OptionDropdownHandler.OPTIONS_SHEET_NAME);
        assertThat(options.getRow(0).getCell(0).getStringCellValue())
                .as("the parent values MATCH searches").isEqualTo("SG_Bachelor");
        assertThat(options.getRow(0).getCell(1).getStringCellValue()).isEqualTo("SG_BEng");
        assertThat(options.getRow(1).getCell(1).getStringCellValue()).isEqualTo("SG_BSc");
        assertThat(options.getRow(0).getCell(2).getStringCellValue())
                .as("the second parent's children go in their own column").isEqualTo("SG_MEng");
        assertThat(options.getRow(1).getCell(2))
                .as("and do not inherit the first parent's second row").isNull();
    }

    @Test
    void aBlankParentResolvesToANameThatExists() {
        // Without _c0 the formula would build a name nothing defines, and Excel drops a validation it
        // cannot resolve — taking the dropdown away from every row, not just the empty ones.
        assertThat(((XSSFWorkbook) write().getWorkbook()).getName("_c4_0"))
                .as("the fallback the IFERROR branch names").isNotNull();
    }

    @Test
    void theParentColumnKeepsItsOwnPlainDropdown() {
        // Only the child is narrowed. The parent is chosen from the full list, and nothing about the
        // cascade should change that.
        Sheet sheet = write();
        assertThat(sheet.getDataValidations()).hasSize(2);
        assertThat(sheet.getDataValidations().stream()
                .anyMatch(v -> v.getValidationConstraint().getExplicitListValues() != null))
                .as("the level column is still a plain list").isTrue();
    }

    @Test
    void twoCascadedColumnsDoNotFightOverTheSameNames() {
        // A defined name belongs to the workbook, not to a sheet or a column. Numbering each cascade
        // from one would have the second column claim names the first already holds — and the parents
        // differ between the two, so it would not fail loudly: it would quietly offer one column the
        // other's values. Two cascaded columns is what the employee template asks for.
        XSSFWorkbook workbook = new XSSFWorkbook();
        Sheet sheet = workbook.createSheet("Template");

        Map<String, List<String>> banksByMethod = new LinkedHashMap<>();
        banksByMethod.put("BankTransfer", List.of("DBS", "OCBC"));
        banksByMethod.put("Cheque", List.of("UOB"));

        Map<Integer, List<String>> options = new LinkedHashMap<>();
        options.put(3, List.of("SG_Bachelor", "SG_Master"));
        options.put(4, List.of("SG_BEng", "SG_BSc", "SG_MEng"));
        options.put(7, List.of("BankTransfer", "Cheque"));
        options.put(8, List.of("DBS", "OCBC", "UOB"));

        Map<Integer, OptionDropdownResolver.Cascade> cascades = new LinkedHashMap<>();
        cascades.put(4, new OptionDropdownResolver.Cascade(3, TRACKS_BY_LEVEL));
        cascades.put(8, new OptionDropdownResolver.Cascade(7, banksByMethod));

        new TestableCascadeHandler(options, cascades).attach(sheet);

        assertThat(workbook.getAllNames().stream().map(Name::getNameName))
                .as("every name distinct, and each says which column it belongs to")
                .containsExactlyInAnyOrder("_c4_1", "_c4_2", "_c4_0", "_c8_1", "_c8_2", "_c8_0");
        assertThat(workbook.getName("_c4_1").getRefersToFormula())
                .isNotEqualTo(workbook.getName("_c8_1").getRefersToFormula());

        String xml = ((XSSFSheet) sheet).getCTWorksheet().toString();
        assertThat(xml).contains("INDIRECT(\"_c4_\"&amp;IFERROR(MATCH($D2,");
        assertThat(xml).contains("INDIRECT(\"_c8_\"&amp;IFERROR(MATCH($H2,");
    }

    /** Exposes {@code attach}, which is protected so the fesod callback stays the only public way in. */
    private static final class TestableCascadeHandler extends OptionDropdownHandler {
        private TestableCascadeHandler(Map<Integer, List<String>> options,
                                       Map<Integer, OptionDropdownResolver.Cascade> cascades) {
            super(options, cascades);
        }

        @Override
        public void attach(Sheet sheet) {
            super.attach(sheet);
        }
    }
}
