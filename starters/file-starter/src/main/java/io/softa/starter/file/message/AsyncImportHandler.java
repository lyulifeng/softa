package io.softa.starter.file.message;

import java.io.InputStream;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.service.FileService;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.entity.ImportHistory;
import io.softa.starter.file.enums.ImportStatus;
import io.softa.starter.file.service.ImportHistoryService;
import io.softa.starter.file.service.ImportService;

@Slf4j
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
        try {
            InputStream inputStream = fileService.downloadStream(importTemplateDTO.getFileId());
            importService.syncImport(importTemplateDTO, inputStream, importHistory);
        } catch (RuntimeException e) {
            markFailed(importHistory, e);
            throw e;
        }
    }

    @Async
    public void asyncHandler(ImportTemplateDTO importTemplateDTO) {
        this.handler(importTemplateDTO);
    }

    /**
     * Persist FAILURE for exceptions the import pipeline never saw.
     *
     * <p>{@code syncImport} records FAILURE (with the error message) itself for anything thrown
     * inside the pipeline — those arrive here already marked and are left alone. This only fills the
     * gap BEFORE the pipeline takes over, e.g. the file download from object storage: such a failure
     * used to leave the record in Processing forever, with the actual error visible only in the
     * server log.
     */
    private void markFailed(ImportHistory importHistory, RuntimeException cause) {
        if (ImportStatus.FAILURE.equals(importHistory.getStatus())) {
            return;
        }
        importHistory.setStatus(ImportStatus.FAILURE);
        importHistory.setErrorMessage(cause.getMessage());
        try {
            importHistoryService.updateOne(importHistory);
        } catch (RuntimeException updateException) {
            // The original failure stays the surfaced error; this one only costs the status flip.
            log.error("Failed to mark import history `{}` as FAILURE after an import error.",
                    importHistory.getId(), updateException);
        }
    }
}
