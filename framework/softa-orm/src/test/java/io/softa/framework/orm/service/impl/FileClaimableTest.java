package io.softa.framework.orm.service.impl;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.orm.entity.FileRecord;
import io.softa.framework.orm.service.FileService.OwnershipStatement;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * {@code assertClaimable} is the single point where "which row owns this file" is enforced, and the
 * expansion path is built on its answer: a File column is turned into a download URL with row-scope
 * waived, on the grounds that reading the row was authorization enough. That only holds if the id in
 * the column got there legitimately — so every case below is really a statement about what the
 * expansion is allowed to trust.
 *
 * <p>The attack it exists to stop: write a stranger's file id into a row you may edit, read the row
 * back, and let the expansion hand you their file. Nothing else in the ORM would refuse that — a File
 * field is an ordinary column.
 */
class FileClaimableTest {

    private static FileRecord record(Long id, String modelName, String rowId) {
        FileRecord record = new FileRecord();
        record.setId(id);
        record.setModelName(modelName);
        record.setRowId(rowId);
        return record;
    }

    private static void assertClaimable(FileServiceImpl service, String modelName, String rowId) {
        assertClaimable(service, modelName, rowId, null);
    }

    private static void assertClaimable(FileServiceImpl service, String modelName, String rowId,
                                        Long callerTenant) {
        Context ctx = new Context();
        ctx.setUserId(7L);
        ctx.setTenantId(callerTenant);
        ContextHolder.runWith(ctx, () -> service.assertClaimable(modelName,
                List.of(new OwnershipStatement(rowId, "attachment", Set.of(9L)))));
    }

    private static FileServiceImpl serviceReturning(FileRecord... records) {
        FileServiceImpl service = spy(new FileServiceImpl());
        doReturn(List.of(records)).when(service).getByIds(anyList());
        return service;
    }

    /** The create-form path: uploaded before the row existed, so the row it will hang on is unknown. */
    @Test
    void anUnclaimedFileOfTheSameModelMayBeAttached() {
        assertClaimable(serviceReturning(record(9L, "EmpAttachment", null)), "EmpAttachment", "5");
    }

    /** Re-saving a record must not fail on the file it already holds. */
    @Test
    void theRowsOwnFileMayBeAttachedAgain() {
        assertClaimable(serviceReturning(record(9L, "EmpAttachment", "5")), "EmpAttachment", "5");
    }

    /**
     * The core case. The file belongs to someone else's row; writing its id into a row the caller may
     * edit is what would turn "I can edit my own record" into "I can read your document".
     */
    @Test
    void aFileOwnedByAnotherRowIsRefused() {
        FileServiceImpl service = serviceReturning(record(9L, "EmpAttachment", "999"));
        assertThrows(PermissionException.class, () -> assertClaimable(service, "EmpAttachment", "5"));
    }

    /**
     * Unclaimed is not the same as free. An upload always records the model it was made against, so a
     * file destined for an Employee cannot be pulled into a row of some other model — a claim writes an
     * id into a field the claimer may edit, and nothing else about that write says whose file it was.
     */
    @Test
    void anUnclaimedFileOfAnotherModelIsRefused() {
        FileServiceImpl service = serviceReturning(record(9L, "Employee", null));
        assertThrows(PermissionException.class, () -> assertClaimable(service, "EmpAttachment", "5"));
    }

    /** A create carries no row id yet, so only the unclaimed case can pass — never someone else's. */
    @Test
    void aCreateCannotAttachAFileAlreadyOwnedBySomeRow() {
        FileServiceImpl service = serviceReturning(record(9L, "EmpAttachment", "999"));
        assertThrows(PermissionException.class, () -> assertClaimable(service, "EmpAttachment", null));
    }

    /**
     * A dangling id fails rather than being dropped: silently saving a row whose attachment vanished
     * looks to the user exactly like saving one that worked, until they come back for the file.
     */
    @Test
    void anIdNamingNoRecordIsRefused() {
        FileServiceImpl service = serviceReturning();
        assertThrows(PermissionException.class, () -> assertClaimable(service, "EmpAttachment", "5"));
    }

    /**
     * One write, one read: however many rows and File fields the write carries, every id is verified
     * against a single batched fetch — the ownership cost of a bulk write must not grow with its row
     * count.
     */
    @Test
    void aWholeWriteIsVerifiedAgainstOneRead() {
        FileServiceImpl service = spy(new FileServiceImpl());
        doReturn(List.of(record(9L, "EmpAttachment", null), record(11L, "EmpAttachment", "5")))
                .when(service).getByIds(anyList());
        Context ctx = new Context();
        ctx.setUserId(7L);

        ContextHolder.runWith(ctx, () -> service.assertClaimable("EmpAttachment", List.of(
                new OwnershipStatement(null, "attachment", Set.of(9L)),
                new OwnershipStatement("5", "attachment", Set.of(11L)))));

        verify(service, times(1)).getByIds(anyList());
    }

    private static FileRecord tenantRecord(Long tenantId) {
        FileRecord record = record(9L, "EmpAttachment", null);
        record.setTenantId(tenantId);
        return record;
    }

    /**
     * Unclaimed is bounded by tenant as well as model. Between upload and save a file is bound to no
     * row, and same-model alone would let a leaked id be pulled into another tenant's row — the one
     * claim theft the row-binding rule cannot see, because there is no binding yet.
     */
    @Test
    void anUnclaimedFileOfAnotherTenantIsRefused() {
        FileServiceImpl service = serviceReturning(tenantRecord(100L));
        assertThrows(PermissionException.class,
                () -> assertClaimable(service, "EmpAttachment", "5", 200L));
    }

    /** The pre-boarding flow: candidate and HR differ as users but share the tenant. */
    @Test
    void anUnclaimedFileOfTheSameTenantMayBeAttached() {
        assertClaimable(serviceReturning(tenantRecord(100L)), "EmpAttachment", "5", 100L);
    }

    /** Records from before the tenant stamp carry null and stay claimable — forward-only. */
    @Test
    void aLegacyUnstampedFileIsNotLockedOut() {
        assertClaimable(serviceReturning(tenantRecord(null)), "EmpAttachment", "5", 200L);
    }
}
