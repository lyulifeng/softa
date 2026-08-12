package io.softa.starter.file.message;

import java.io.InputStream;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.FileService;
import io.softa.starter.file.dto.ImportTemplateDTO;
import io.softa.starter.file.entity.ImportHistory;
import io.softa.starter.file.enums.ImportStatus;
import io.softa.starter.file.service.ImportHistoryService;
import io.softa.starter.file.service.ImportService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * A failure BEFORE the import pipeline takes over (e.g. the file download) must not leave the
 * history record in Processing forever — that used to be exactly what happened, with the error
 * visible only in the server log. Failures inside the pipeline are persisted by syncImport itself
 * and must not be double-written here.
 */
class AsyncImportHandlerTest {

    private final ImportService importService = mock(ImportService.class);
    private final FileService fileService = mock(FileService.class);
    private final ImportHistoryService importHistoryService = mock(ImportHistoryService.class);
    private final AsyncImportHandler handler = new AsyncImportHandler();

    private ImportHistory history;
    private ImportTemplateDTO dto;

    @BeforeEach
    void setUp() {
        ReflectionTestUtils.setField(handler, "importService", importService);
        ReflectionTestUtils.setField(handler, "fileService", fileService);
        ReflectionTestUtils.setField(handler, "importHistoryService", importHistoryService);

        history = new ImportHistory();
        history.setId(20L);
        history.setStatus(ImportStatus.PROCESSING);
        when(importHistoryService.getById(20L)).thenReturn(Optional.of(history));

        dto = new ImportTemplateDTO();
        dto.setHistoryId(20L);
        dto.setFileId(99L);
    }

    @Test
    void downloadFailureMarksTheHistoryFailedInsteadOfLeavingItProcessing() {
        when(fileService.downloadStream(99L)).thenThrow(new RuntimeException("object storage is down"));

        assertThrows(RuntimeException.class, () -> handler.handler(dto));

        ArgumentCaptor<ImportHistory> updated = ArgumentCaptor.forClass(ImportHistory.class);
        verify(importHistoryService).updateOne(updated.capture());
        assertEquals(ImportStatus.FAILURE, updated.getValue().getStatus());
        assertEquals("object storage is down", updated.getValue().getErrorMessage());
    }

    @Test
    void pipelineFailureAlreadyPersistedBySyncImportIsNotDoubleWritten() {
        when(fileService.downloadStream(anyLong())).thenReturn(InputStream.nullInputStream());
        // syncImport persists FAILURE on the shared instance before rethrowing.
        when(importService.syncImport(any(), any(), any())).thenAnswer(inv -> {
            history.setStatus(ImportStatus.FAILURE);
            history.setErrorMessage("row error");
            throw new RuntimeException("row error");
        });

        assertThrows(RuntimeException.class, () -> handler.handler(dto));

        verify(importHistoryService, never()).updateOne(any(ImportHistory.class));
    }

    @Test
    void updateFailureDoesNotMaskTheOriginalError() {
        when(fileService.downloadStream(99L)).thenThrow(new RuntimeException("original"));
        doThrow(new RuntimeException("update failed")).when(importHistoryService).updateOne(any(ImportHistory.class));

        RuntimeException thrown = assertThrows(RuntimeException.class, () -> handler.handler(dto));
        assertEquals("original", thrown.getMessage());
    }
}
