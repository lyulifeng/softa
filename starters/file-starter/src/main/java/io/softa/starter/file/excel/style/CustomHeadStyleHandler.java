package io.softa.starter.file.excel.style;

import java.util.List;
import org.apache.fesod.sheet.write.handler.CellWriteHandler;
import org.apache.fesod.sheet.write.handler.context.CellWriteHandlerContext;
import org.apache.fesod.sheet.write.metadata.style.WriteCellStyle;
import org.apache.fesod.sheet.write.metadata.style.WriteFont;
import org.springframework.util.CollectionUtils;

import io.softa.starter.file.constant.FileConstant;

/**
 * Custom header style
 */
public class CustomHeadStyleHandler implements CellWriteHandler {

    // dynamic required headers
    private final List<String> requiredHeaderList;

    public CustomHeadStyleHandler(List<String> requiredHeaderList) {
        this.requiredHeaderList = requiredHeaderList;
    }

    @Override
    public void afterCellDispose(CellWriteHandlerContext context) {
        if (!CollectionUtils.isEmpty(requiredHeaderList) && context.getHead()) {
            if (requiredHeaderList.contains(context.getHeadData().getHeadNameList().getFirst())) {
                WriteCellStyle writeCellStyle = context.getFirstCellData().getOrCreateStyle();
                // Merges existing cell styles
                WriteFont customFont = new WriteFont();
                WriteFont.merge(writeCellStyle.getWriteFont(), customFont);
                customFont.setColor(FileConstant.REQUIRED_EXCEL_HEAD_FONT_COLOR);
                writeCellStyle.setWriteFont(customFont);
            }
        }
    }

}
