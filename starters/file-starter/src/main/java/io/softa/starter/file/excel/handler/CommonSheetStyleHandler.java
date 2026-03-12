package io.softa.starter.file.excel.handler;

import org.apache.fesod.sheet.write.handler.RowWriteHandler;
import org.apache.fesod.sheet.write.handler.context.RowWriteHandlerContext;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;

import io.softa.starter.file.constant.FileConstant;

/**
 * Normalize generated sheet width and header row height across Excel export paths.
 */
public class CommonSheetStyleHandler implements RowWriteHandler {

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        if (!context.getHead()) {
            return;
        }

        Sheet sheet = context.getWriteSheetHolder().getSheet();
        sheet.setDefaultColumnWidth(FileConstant.DEFAULT_EXCEL_COLUMN_WIDTH);

        Row titleRow = sheet.getRow(0);
        if (titleRow != null) {
            titleRow.setHeightInPoints(FileConstant.DEFAULT_EXCEL_HEAD_ROW_HEIGHT);
        }
    }
}
