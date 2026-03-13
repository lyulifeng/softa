package io.softa.starter.file.excel.export;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.apache.fesod.sheet.write.handler.WriteHandler;

@Data
@NoArgsConstructor
@AllArgsConstructor
public class ExcelSheetData {

    private String sheetName;

    private List<String> headers;

    private List<List<Object>> rowsTable;

    private WriteHandler[] writeHandlers;
}
