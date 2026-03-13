package io.softa.starter.file.excel.style;

import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.HorizontalAlignment;
import org.apache.poi.ss.usermodel.VerticalAlignment;

import io.softa.starter.file.constant.FileConstant;

/**
 * Normalize generated header style across Excel export paths.
 */
public class CommonHeadStyleHandler implements CellWriteHandler {

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        if (!context.getHead()) {
            return;
        }
        WriteCellStyle writeCellStyle = context.getFirstCellData().getOrCreateStyle();
        writeCellStyle.setFillForegroundColor(FileConstant.DEFAULT_EXCEL_HEAD_BACKGROUND_COLOR);
        writeCellStyle.setFillPatternType(FillPatternType.SOLID_FOREGROUND);
        writeCellStyle.setHorizontalAlignment(HorizontalAlignment.CENTER);
        writeCellStyle.setVerticalAlignment(VerticalAlignment.CENTER);
    }
}
