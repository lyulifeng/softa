package io.softa.starter.file.constant;

import org.apache.poi.ss.usermodel.IndexedColors;

/**
 * Constants for file starter.
 */
public interface FileConstant {

    String DEFAULT_EXCEL_FONT_NAME = "Calibri";

    int DEFAULT_EXCEL_COLUMN_WIDTH = 24;
    short DEFAULT_EXCEL_HEAD_ROW_HEIGHT = 22;
    short DEFAULT_EXCEL_BODY_FONT_SIZE = 11;
    short DEFAULT_EXCEL_HEAD_FONT_SIZE = 12;

    short DEFAULT_EXCEL_HEAD_FONT_COLOR = IndexedColors.WHITE.getIndex();
    short DEFAULT_EXCEL_HEAD_BACKGROUND_COLOR = IndexedColors.DARK_TEAL.getIndex();
    short REQUIRED_EXCEL_HEAD_FONT_COLOR = IndexedColors.RED.getIndex();
}
