package io.softa.starter.file.excel.style;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;

import org.apache.fesod.sheet.ExcelWriter;
import org.apache.fesod.sheet.FesodSheet;
import org.apache.fesod.sheet.write.metadata.WriteSheet;

import io.softa.starter.file.excel.export.support.ExcelWriterFactory;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.enums.FieldType;
import io.softa.framework.orm.meta.MetaField;
import io.softa.framework.orm.meta.ModelManager;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;

/**
 * The handler driven the way production drives it — through the writer, not by hand.
 *
 * <p>What a reader sees is decided by two different things: the column's default style, which an
 * empty template column hands to whatever gets typed into it, and the style on a written cell, which
 * an export carries. A handler that set only one of them would pass a hand-built assertion and still
 * leave half the files rendering by locale, so both are read back out of real bytes.
 */
class TemporalColumnFormatWorkbookTest {

    private static final List<List<String>> HEADS = List.of(
            List.of("Employee Code"), List.of("Date Of Birth"), List.of("Created Time"));

    /** Column 1 is a DATE, column 2 a DATE_TIME, column 0 a plain string. */
    private void withMetadata(Runnable body) {
        try (MockedStatic<ModelManager> mm = Mockito.mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getLastFieldOfCascaded(any(), any())).thenAnswer(inv -> {
                String fieldName = inv.getArgument(1);
                FieldType type = switch (fieldName) {
                    case "dateOfBirth" -> FieldType.DATE;
                    case "createdTime" -> FieldType.DATE_TIME;
                    default -> FieldType.STRING;
                };
                MetaField metaField = new MetaField();
                ReflectionTestUtils.setField(metaField, "fieldType", type);
                return metaField;
            });
            body.run();
        }
    }

    /**
     * Through {@link ExcelWriterFactory}, because that is what every export path uses and the shared
     * handlers it registers are the whole difficulty: {@code CommonFontStyleHandler} calls
     * {@code getOrCreateStyle()} on every cell, so every written cell ends up with an explicit style
     * — and an explicit style is what beats a column default. Building the sheet by hand here would
     * quietly drop that and prove the easy half.
     */
    private byte[] write(List<List<Object>> rows) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(out).build()) {
            WriteSheet sheet = new ExcelWriterFactory()
                    .createSheetBuilder(0, "Employees", HEADS,
                            TemporalColumnFormatHandler.forFields(
                                    "Employee", List.of("code", "dateOfBirth", "createdTime")))
                    .build();
            writer.write(rows, sheet);
            writer.finish();
        }
        return out.toByteArray();
    }

    private Workbook read(List<List<Object>> rows) throws Exception {
        return new XSSFWorkbook(new ByteArrayInputStream(write(rows)));
    }

    private static String formatOfColumn(Sheet sheet, int column) {
        return sheet.getColumnStyle(column) == null ? null
                : sheet.getColumnStyle(column).getDataFormatString();
    }

    @Test
    void anEmptyTemplateColumnCarriesTheFormatATypedDateWillTake() throws Exception {
        // No data rows, so there is no cell to style — a template is a header row and nothing else.
        // The column default is the whole mechanism here: it is what Excel gives the cell the reader
        // creates by typing, instead of falling back to the machine's locale.
        MetaFieldHolder holder = new MetaFieldHolder();
        withMetadata(() -> holder.run(() -> {
            try (Workbook workbook = read(Collections.emptyList())) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(formatOfColumn(sheet, 1)).isEqualTo("yyyy-MM-dd");
                assertThat(formatOfColumn(sheet, 2)).isEqualTo("yyyy-MM-dd HH:mm:ss");
                assertThat(formatOfColumn(sheet, 0))
                        .as("a string column is left alone").isNotEqualTo("yyyy-MM-dd");
            }
        }));
    }

    @Test
    void theWriterAlreadyFormatsAWrittenTemporalValue() {
        // Why the export paths were left alone. Written with no handler at all, a LocalDate already
        // comes out as yyyy-MM-dd — so the reported problem was only ever the empty column, and
        // wiring the handler into the exports would have been motion with nothing behind it. Pinned
        // rather than remembered: if the writer ever stops doing this, the exports need the handler
        // and this is where that shows up.
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (ExcelWriter writer = FesodSheet.write(out).build()) {
            WriteSheet sheet = new ExcelWriterFactory().createSheetBuilder(0, "Employees", HEADS).build();
            writer.write(List.of(List.<Object>of("E001", LocalDate.of(1999, 1, 1),
                    LocalDateTime.of(2026, 9, 6, 14, 30, 0))), sheet);
            writer.finish();
        }
        MetaFieldHolder holder = new MetaFieldHolder();
        holder.run(() -> {
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(sheet.getRow(1).getCell(1).getCellStyle().getDataFormatString())
                        .isEqualTo("yyyy-MM-dd");
                assertThat(sheet.getRow(1).getCell(2).getCellStyle().getDataFormatString())
                        .isEqualTo("yyyy-MM-dd HH:mm:ss");
                assertThat(formatOfColumn(sheet, 1))
                        .as("and the column it sits in is still General — the gap a template falls into")
                        .isEqualTo("General");
            }
        });
    }

    @Test
    void aSheetWithNoTemporalColumnGetsNoHandlerAtAll() {
        // forFields returns null, which the sheet builder skips — nothing registered, nothing to undo.
        withMetadata(() -> assertThat(TemporalColumnFormatHandler.forFields("Employee", List.of("code")))
                .isNull());
    }

    @Test
    void oneStylePerFormatAcrossTheColumnsThatShareIt() {
        // A workbook caps at 64k cell styles. Two date columns must not mean two identical styles,
        // which is what a per-column createCellStyle() would give.
        MetaFieldHolder holder = new MetaFieldHolder();
        withMetadata(() -> holder.run(() -> {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            try (ExcelWriter writer = FesodSheet.write(out).build()) {
                WriteSheet sheet = new ExcelWriterFactory()
                        .createSheetBuilder(0, "Employees",
                                List.of(List.of("Code"), List.of("Date A"), List.of("Date B")),
                                TemporalColumnFormatHandler.forFields(
                                        "Employee", List.of("code", "dateOfBirth", "dateOfBirth")))
                        .build();
                writer.write(Collections.emptyList(), sheet);
                writer.finish();
            }
            try (Workbook workbook = new XSSFWorkbook(new ByteArrayInputStream(out.toByteArray()))) {
                Sheet sheet = workbook.getSheetAt(0);
                assertThat(formatOfColumn(sheet, 1)).isEqualTo("yyyy-MM-dd");
                assertThat(formatOfColumn(sheet, 2)).isEqualTo("yyyy-MM-dd");
                assertThat(sheet.getColumnStyle(1).getIndex())
                        .as("both columns point at the same style object")
                        .isEqualTo(sheet.getColumnStyle(2).getIndex());
            }
        }));
    }

    /** Lets a lambda throw, so the assertions read as they would outside one. */
    private static final class MetaFieldHolder {
        void run(ThrowingBody body) {
            try {
                body.run();
            } catch (Exception e) {
                throw new IllegalStateException(e);
            }
        }

        interface ThrowingBody {
            void run() throws Exception;
        }
    }
}
