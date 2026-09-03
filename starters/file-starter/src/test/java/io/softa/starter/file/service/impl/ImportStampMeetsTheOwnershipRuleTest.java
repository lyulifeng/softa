package io.softa.starter.file.service.impl;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.orm.entity.FileRecord;
import io.softa.framework.orm.service.FileService.OwnershipStatement;
import io.softa.framework.orm.service.impl.FileServiceImpl;
import io.softa.starter.file.entity.ExportHistory;
import io.softa.starter.file.entity.ImportHistory;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.mockito.Mockito.CALLS_REAL_METHODS;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The stamp an import puts on its file, run against the rule that decides who may claim it.
 *
 * <p>This is the seam the 2.6.0 regression fell through. {@code FileClaimableTest} proved the rule
 * behaves as designed; the import tests proved the pipeline behaves as designed; each lived in its
 * own module and neither ever saw the other's half. Between them, every import in the product failed
 * with "File … is not yours to attach" and nothing went red.
 *
 * <p>So this asks the one question neither side could: given what file-starter actually stamps, does
 * softa-orm's rule let the history row take it? The stamp is read from the same class constant the
 * production code uses, not retyped, so changing the stamp moves this test with it.
 *
 * <p>Not a full end-to-end run — there is no OSS and no database here. The real
 * {@link FileServiceImpl#assertClaimable} executes; only the record lookup is supplied.
 */
class ImportStampMeetsTheOwnershipRuleTest {

    private static final Long FILE_ID = 4242L;

    /**
     * A FileServiceImpl whose only stub is the record lookup — assertClaimable itself is real.
     *
     * <p>A mock rather than a subclass: EntityServiceImpl reads its own parameterised superclass to
     * work out which model it serves, and an anonymous subclass hands it a plain Class instead.
     * Mockito builds the instance without running that, which is all this test needs.
     */
    private static FileServiceImpl serviceHolding(FileRecord record) {
        FileServiceImpl service = mock(FileServiceImpl.class, CALLS_REAL_METHODS);
        doReturn(List.of(record)).when(service).getByIds(List.of(record.getId()));
        return service;
    }

    /** What {@code fileService.uploadFile(model, file)} leaves behind: a record naming the model it
     *  was uploaded against, bound to no row yet. */
    private static FileRecord uploadedAgainst(String modelName) {
        FileRecord record = new FileRecord();
        record.setId(FILE_ID);
        record.setModelName(modelName);
        record.setRowId(null);
        return record;
    }

    private static List<OwnershipStatement> attachingTo(String fieldName) {
        return List.of(new OwnershipStatement("1", fieldName, Set.of(FILE_ID)));
    }

    @Test
    @DisplayName("an import's file, stamped as the fix stamps it, may be attached to its history row")
    void theImportStampIsAccepted() {
        FileServiceImpl fileService = serviceHolding(uploadedAgainst(ImportHistory.class.getSimpleName()));

        assertThatCode(() -> fileService.assertClaimable(
                ImportHistory.class.getSimpleName(), attachingTo("originalFileId")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("the same file stamped with the model being imported is refused — the 2.6.0 failure")
    void theOldStampIsRefused() {
        // Employee is what ImportServiceImpl used to pass. Kept as a literal on purpose: this is the
        // value that broke production, and it must stay refused even if nothing references it again.
        FileServiceImpl fileService = serviceHolding(uploadedAgainst("Employee"));

        assertThatThrownBy(() -> fileService.assertClaimable(
                ImportHistory.class.getSimpleName(), attachingTo("originalFileId")))
                .isInstanceOf(PermissionException.class)
                .hasMessageContaining("not yours to attach");
    }

    @Test
    @DisplayName("the failed-data file is accepted on the same history row's other File field")
    void theFailedDataFileIsAcceptedToo() {
        // Two File fields on one model, one stamp: failedFileId has to pass the same check, and it is
        // the field a user actually clicks in My Import History.
        FileServiceImpl fileService = serviceHolding(uploadedAgainst(ImportHistory.class.getSimpleName()));

        assertThatCode(() -> fileService.assertClaimable(
                ImportHistory.class.getSimpleName(), attachingTo("failedFileId")))
                .doesNotThrowAnyException();
    }

    @Test
    @DisplayName("an export's file may be attached to its history row, and an exported model's may not")
    void theExportStampIsAcceptedAndTheOldOneIsNot() {
        FileServiceImpl stamped = serviceHolding(uploadedAgainst(ExportHistory.class.getSimpleName()));
        assertThatCode(() -> stamped.assertClaimable(
                ExportHistory.class.getSimpleName(), attachingTo("fileId")))
                .doesNotThrowAnyException();

        FileServiceImpl mis = serviceHolding(uploadedAgainst("Employee"));
        assertThatThrownBy(() -> mis.assertClaimable(
                ExportHistory.class.getSimpleName(), attachingTo("fileId")))
                .isInstanceOf(PermissionException.class);
    }
}
