package io.softa.starter.file.message;

import java.io.InputStream;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.service.FileService;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.entity.ImportHistory;
import io.softa.starter.file.service.ImportHistoryService;
import io.softa.starter.file.service.ImportService;

@Component
public class AsyncImportHandler {

    @Lazy
    @Autowired
    private ImportService importService;

    @Autowired
    private FileService fileService;

    @Autowired
    private ImportHistoryService importHistoryService;

    public void handler(ImportTemplateDTO importTemplateDTO) {
        ImportHistory importHistory = importHistoryService.getById(importTemplateDTO.getHistoryId())
                .orElseThrow(() -> new IllegalArgumentException("The import history with ID `{0}` does not exist", importTemplateDTO.getHistoryId()));
        InputStream inputStream = fileService.downloadStream(importTemplateDTO.getFileId());
        importService.syncImport(importTemplateDTO, inputStream, importHistory);
    }

    @Async
    public void asyncHandler(ImportTemplateDTO importTemplateDTO) {
        this.handler(importTemplateDTO);
    }
}
