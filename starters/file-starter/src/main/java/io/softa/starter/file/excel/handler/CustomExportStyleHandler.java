package io.softa.starter.file.excel.handler;

import org.apache.fesod.sheet.write.handler.RowWriteHandler;
import org.apache.fesod.sheet.write.handler.context.RowWriteHandlerContext;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFCellStyle;

import io.softa.starter.file.constant.FileConstant;

/**
 * custom export style handler
 * head color
 */
public class CustomExportStyleHandler implements RowWriteHandler {

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (!context.getHead()) {
            return;
        }

        Sheet sheet = context.getWriteSheetHolder().getSheet();
        Workbook workbook = context.getWriteWorkbookHolder().getWorkbook();
        // Set column width
        sheet.setDefaultColumnWidth(FileConstant.DEFAULT_EXCEL_COLUMN_WIDTH);

        // Set the header row style
        Row titleRow = sheet.getRow(0);
        // Set row height
        titleRow.setHeightInPoints(FileConstant.DEFAULT_EXCEL_HEAD_ROW_HEIGHT);
        // Set header row font style
        Font titleFont = workbook.createFont();
        titleFont.setBold(true);
        titleFont.setFontName(FileConstant.DEFAULT_EXCEL_FONT_NAME);
        titleFont.setFontHeightInPoints(FileConstant.DEFAULT_EXCEL_HEAD_FONT_SIZE);
        titleFont.setColor(FileConstant.DEFAULT_EXCEL_HEAD_FONT_COLOR);
        XSSFCellStyle titleStyle = (XSSFCellStyle) workbook.createCellStyle();
        titleStyle.setFont(titleFont);
        titleStyle.setFillForegroundColor(FileConstant.DEFAULT_EXCEL_HEAD_BACKGROUND_COLOR);
        titleStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        titleStyle.setAlignment(HorizontalAlignment.CENTER);
        titleStyle.setVerticalAlignment(VerticalAlignment.CENTER);

        // Apply the default style to header row
        for (int i = 0; i < titleRow.getLastCellNum(); i++) {
            Cell cell = titleRow.getCell(i);
            cell.setCellStyle(titleStyle);
        }
    }

}
