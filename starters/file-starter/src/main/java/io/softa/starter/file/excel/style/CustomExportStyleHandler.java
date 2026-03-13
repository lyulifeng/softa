package io.softa.starter.file.excel.style;

import org.apache.fesod.sheet.write.handler.RowWriteHandler;
import org.apache.fesod.sheet.write.handler.context.RowWriteHandlerContext;

/**
 * Reserved for template-specific row level styling.
 */
public class CustomExportStyleHandler implements RowWriteHandler {

    @Override
    public void afterRowDispose(RowWriteHandlerContext context) {
        // Common head styling is handled by shared handlers.
    }
}
