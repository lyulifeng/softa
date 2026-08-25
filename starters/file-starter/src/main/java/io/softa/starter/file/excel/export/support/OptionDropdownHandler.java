package io.softa.starter.file.excel.export.support;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.apache.fesod.sheet.write.handler.SheetWriteHandler;
import org.apache.fesod.sheet.write.handler.context.SheetWriteHandlerContext;
import org.apache.poi.ss.usermodel.DataValidation;
import org.apache.poi.ss.usermodel.DataValidationConstraint;
import org.apache.poi.ss.usermodel.DataValidationHelper;
import org.apache.poi.ss.usermodel.Name;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddressList;

/**
 * Turns the option-backed columns of an import template into Excel dropdowns holding item codes.
 *
 * <p>Takes the values already resolved — a column index mapped to its allowed item codes — and does no
 * IO of its own. That is deliberate: this runs inside workbook serialisation, and reaching for a
 * database connection at that point ties a query to the lifetime of a file write.
 *
 * <p>Two ways to attach a list, chosen per column by its total length:
 *
 * <ul>
 *   <li><b>Inline list</b> — the values go straight into the validation formula. Excel caps that
 *       formula at 255 characters and POI enforces it by throwing, so a set that serialises longer
 *       cannot use this form. Most do fit: the option sets these columns point at run 30–50
 *       characters in total.
 *   <li><b>Range reference</b> — the values are written down a column of a hidden sheet and the
 *       formula points at that range. The formula is then a fixed-length address, so the 255-character
 *       cap stops applying however many options there are.
 * </ul>
 *
 * <p>Inline is preferred where it fits, because the hidden sheet has a cost of its own: a reader can
 * unhide or delete it, and a deleted sheet leaves every dropdown pointing at a range that no longer
 * exists. Paying that only for the columns that need it keeps the common template to the two sheets
 * it has today.
 *
 * <p>A column with no resolvable options is skipped rather than failing the download. Missing option
 * data is a reason to hand back a template without one dropdown; it is not a reason to hand back
 * nothing.
 *
 * <p>Only the workbook's first sheet is touched — the template's own, the one people type into. The
 * instruction sheet beside it is prose.
 */
@Slf4j
public class OptionDropdownHandler implements SheetWriteHandler {

    /** Excel's limit on a data-validation formula, which an inline list has to fit inside. */
    private static final int INLINE_FORMULA_LIMIT = 255;

    /**
     * Rows below the header the dropdown covers. Excel wants a bounded range, and a row past the end
     * gets neither the list nor the validation — so this is also where a very large paste stops being
     * checked. Set well above any import anyone hand-fills.
     */
    private static final int DROPDOWN_ROWS = 5000;

    /** The sheet these columns belong to: the template's first, which is the one people type into. */
    private static final int MAIN_SHEET_INDEX = 0;

    /** Name of the sheet holding the values that do not fit inline. */
    static final String OPTIONS_SHEET_NAME = "_options";

    /** Prefix of the defined names holding one parent's children. Numbered, never derived from text. */
    static final String CASCADE_NAME_PREFIX = "_c";

    /** Column index (0-based, on the main sheet) → the item codes allowed in it. */
    private final Map<Integer, List<String>> optionsByColumn;

    /** Column index → the column it is narrowed by, for the few columns that are. */
    private final Map<Integer, OptionDropdownResolver.Cascade> cascadesByColumn;

    public OptionDropdownHandler(Map<Integer, List<String>> optionsByColumn) {
        this(optionsByColumn, Map.of());
    }

    public OptionDropdownHandler(Map<Integer, List<String>> optionsByColumn,
                                 Map<Integer, OptionDropdownResolver.Cascade> cascadesByColumn) {
        this.optionsByColumn = optionsByColumn == null ? Map.of() : new LinkedHashMap<>(optionsByColumn);
        this.cascadesByColumn = cascadesByColumn == null ? Map.of() : new LinkedHashMap<>(cascadesByColumn);
    }

    @Override
    public void afterSheetCreate(SheetWriteHandlerContext context) {
        Sheet sheet = context.getWriteSheetHolder() == null ? null : context.getWriteSheetHolder().getSheet();
        if (sheet != null) {
            attach(sheet);
        }
    }

    /**
     * Attaches the dropdowns to a sheet.
     *
     * <p>Separate from the callback so the decision it makes — inline list or hidden sheet — can be
     * exercised against a plain workbook. Driving it through a write pipeline instead would be testing
     * the Excel library.
     */
    protected void attach(Sheet sheet) {
        if (optionsByColumn.isEmpty()) {
            return;
        }
        Workbook workbook = sheet.getWorkbook();
        // Identified by position, not by name. Excel caps a sheet name at 31 characters and truncates
        // silently past it, and most template names are longer than that — matching on the name meant
        // no sheet ever matched and the whole feature did nothing, with no error to show for it.
        if (workbook.getSheetIndex(sheet) != MAIN_SHEET_INDEX) {
            return;
        }
        DataValidationHelper helper = sheet.getDataValidationHelper();
        Sheet optionsSheet = null;
        int nextOptionsColumn = 0;

        for (Map.Entry<Integer, List<String>> entry : optionsByColumn.entrySet()) {
            int columnIndex = entry.getKey();
            List<String> codes = entry.getValue();
            if (codes == null || codes.isEmpty()) {
                continue;
            }
            try {
                DataValidationConstraint constraint;
                OptionDropdownResolver.Cascade cascade = cascadesByColumn.get(columnIndex);
                if (cascade != null) {
                    if (optionsSheet == null) {
                        optionsSheet = createHiddenOptionsSheet(workbook);
                    }
                    int parentValuesColumn = nextOptionsColumn;
                    nextOptionsColumn = writeCascade(workbook, optionsSheet, nextOptionsColumn, cascade);
                    constraint = helper.createFormulaListConstraint(
                            cascadeFormula(cascade, parentValuesColumn));
                } else if (fitsInline(codes)) {
                    constraint = helper.createExplicitListConstraint(codes.toArray(new String[0]));
                } else {
                    if (optionsSheet == null) {
                        optionsSheet = createHiddenOptionsSheet(workbook);
                    }
                    writeOptionsColumn(optionsSheet, nextOptionsColumn, codes);
                    constraint = helper.createFormulaListConstraint(
                            rangeReference(nextOptionsColumn, codes.size()));
                    nextOptionsColumn++;
                }
                DataValidation validation = helper.createValidation(constraint,
                        new CellRangeAddressList(1, DROPDOWN_ROWS, columnIndex, columnIndex));
                // Reject anything not on the list, and say why. Without this Excel accepts a typo
                // silently and the row only fails later, during the import itself.
                validation.setShowErrorBox(true);
                // Reads backwards, and has to. POI writes this straight through to the file's
                // showDropDown attribute, inverted: setSuppressDropDownArrow(true) produces
                // showDropDown="false". And Excel reads that attribute inverted from its own name —
                // showDropDown="true" is what it writes when you UNCHECK "In-cell dropdown". The two
                // inversions do not cancel: passing false here hides the arrow, which is how this
                // shipped once, with the validations all present and no way to see them. Verified
                // against the generated XML, not against POI's getter, which reports its own naming.
                validation.setSuppressDropDownArrow(true);
                sheet.addValidationData(validation);
            } catch (RuntimeException e) {
                // One column's dropdown is not worth the whole template. Logged rather than swallowed
                // so "why is this column missing its dropdown" is answerable without a debugger.
                log.warn("Skipped the dropdown on column {} of sheet '{}': {}",
                        columnIndex, sheet.getSheetName(), e.getMessage());
            }
        }
    }

    /**
     * Lays a cascade out on the hidden sheet: one column of parent values, then one column per parent
     * holding that parent's children, each of the latter given a defined name.
     *
     * <p><b>The names are numbered, never derived from the parent's text.</b> Excel is strict about
     * what a defined name may contain — no spaces, no leading digit, nothing that reads as a cell
     * reference — so keying them by value would mean sanitising every parent and reproducing the exact
     * same sanitisation inside the formula, in nested SUBSTITUTE calls that depend on what the values
     * happen to be. Numbering by position sidesteps all of it: the formula finds the position with
     * MATCH and builds {@code _c3} from it, and every name is valid by construction.
     *
     * @return the next free column on the hidden sheet
     */
    private static int writeCascade(Workbook workbook, Sheet optionsSheet, int firstColumn,
                                    OptionDropdownResolver.Cascade cascade) {
        List<String> parents = List.copyOf(cascade.valuesByParentValue().keySet());
        writeOptionsColumn(optionsSheet, firstColumn, parents);
        int column = firstColumn + 1;
        for (int i = 0; i < parents.size(); i++) {
            List<String> children = cascade.valuesByParentValue().get(parents.get(i));
            writeOptionsColumn(optionsSheet, column, children);
            Name name = workbook.createName();
            // Positions are 1-based in the formula, so the first parent is _c1 and _c0 stays free to
            // mean "no parent chosen yet".
            name.setNameName(CASCADE_NAME_PREFIX + (i + 1));
            name.setRefersToFormula(rangeReference(column, children.size()));
            column++;
        }
        // _c0: a single blank cell. A child cell whose parent is still empty has to point somewhere —
        // pointing at a name that does not exist makes Excel treat the whole validation as broken and
        // drop the dropdown, blank parent or not.
        Name blank = workbook.createName();
        blank.setNameName(CASCADE_NAME_PREFIX + "0");
        blank.setRefersToFormula(rangeReference(column, 1));
        writeOptionsColumn(optionsSheet, column, List.of(""));
        return column + 1;
    }

    /**
     * {@code INDIRECT("_c" & IFERROR(MATCH($D2,_options!$A$1:$A$5,0),0))}
     *
     * <p>Relative on the row so the rule travels down the column, absolute on the parent's column so
     * it keeps pointing at the parent. IFERROR covers the cell whose parent is still blank, which
     * would otherwise leave MATCH returning #N/A and the name unresolvable.
     */
    private static String cascadeFormula(OptionDropdownResolver.Cascade cascade, int parentValuesColumn) {
        int parentCount = cascade.valuesByParentValue().size();
        String parentCell = "$" + columnLetter(cascade.parentColumn()) + "2";
        String parentRange = rangeReference(parentValuesColumn, parentCount);
        return String.format("INDIRECT(\"%s\"&IFERROR(MATCH(%s,%s,0),0))",
                CASCADE_NAME_PREFIX, parentCell, parentRange);
    }

    /**
     * Whether the values can go into the validation formula itself rather than onto the hidden sheet.
     *
     * <p>Two things disqualify them. The obvious one is length, which Excel caps and POI enforces by
     * throwing. The other is the separator: an inline list is comma-separated with no escaping, so a
     * value containing a comma silently becomes two entries — {@code JPMORGAN CHASE BANK, N.A.} offers
     * itself as {@code JPMORGAN CHASE BANK} and {@code N.A.}, neither of which imports. A double quote
     * breaks the formula in the same way.
     *
     * <p>Length alone was the whole test while the only inline candidates were option sets of short
     * codes. Relation columns brought in names people wrote, and punctuation with them.
     */
    private static boolean fitsInline(List<String> codes) {
        if (inlineLength(codes) > INLINE_FORMULA_LIMIT) {
            return false;
        }
        for (String code : codes) {
            if (code.indexOf(',') >= 0 || code.indexOf('"') >= 0) {
                return false;
            }
        }
        return true;
    }

    /** Length of the values as Excel stores them inline: comma-separated, no quotes. */
    private static int inlineLength(List<String> codes) {
        int length = codes.size() - 1;
        for (String code : codes) {
            length += code.length();
        }
        return length;
    }

    private static Sheet createHiddenOptionsSheet(Workbook workbook) {
        Sheet existing = workbook.getSheet(OPTIONS_SHEET_NAME);
        if (existing != null) {
            return existing;
        }
        Sheet sheet = workbook.createSheet(OPTIONS_SHEET_NAME);
        workbook.setSheetHidden(workbook.getSheetIndex(sheet), true);
        return sheet;
    }

    private static void writeOptionsColumn(Sheet sheet, int columnIndex, List<String> codes) {
        for (int i = 0; i < codes.size(); i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                row = sheet.createRow(i);
            }
            row.createCell(columnIndex).setCellValue(codes.get(i));
        }
    }

    /** {@code _options!$A$1:$A$n} — absolute, so copying a cell does not shift the source. */
    private static String rangeReference(int columnIndex, int size) {
        String column = columnLetter(columnIndex);
        return String.format("%s!$%s$1:$%s$%d", OPTIONS_SHEET_NAME, column, column, size);
    }

    private static String columnLetter(int columnIndex) {
        StringBuilder letters = new StringBuilder();
        int index = columnIndex;
        while (index >= 0) {
            letters.insert(0, (char) ('A' + index % 26));
            index = index / 26 - 1;
        }
        return letters.toString();
    }
}
