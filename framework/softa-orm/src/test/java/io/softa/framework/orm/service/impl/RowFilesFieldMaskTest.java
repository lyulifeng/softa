package io.softa.framework.orm.service.impl;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.entity.FileRecord;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.PermissionService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Row access is not field access.
 *
 * <p>A masked column never reaches the expansion path — Layer C drops it from the SELECT, so the file
 * id behind it is not read at all. {@code getRowFiles} fetches by ROW and skips that step, so it has
 * to apply the mask itself. Without this a document behind a sensitive field set stays hidden on one
 * read and is handed over on the other, and the field set looks configured while protecting nothing on
 * half the surface — the worst shape a control can take.
 */
class RowFilesFieldMaskTest {

    private static FileRecord record(Long id, String fieldName) {
        FileRecord record = new FileRecord();
        record.setId(id);
        record.setModelName("Employee");
        record.setRowId("100");
        record.setFieldName(fieldName);
        return record;
    }

    private static List<Long> readable(Set<String> blocked, FileRecord... stored) {
        FileServiceImpl service = new FileServiceImpl();
        PermissionService permissions = mock(PermissionService.class);
        when(permissions.getUserBlockedModelFields(anyString(), eq(AccessType.READ))).thenReturn(blocked);
        ReflectionTestUtils.setField(service, "permissionService", permissions);
        return service.readableFiles("Employee", List.of(stored)).stream().map(FileRecord::getId).toList();
    }

    @Test
    void aFileBehindAMaskedFieldIsWithheld() {
        assertEquals(List.of(1L),
                readable(Set.of("bankStatementFile"),
                        record(1L, "photoFile"),
                        record(2L, "bankStatementFile")),
                "the masked field's file must not come back through this path either");
    }

    /** No column speaks for a file recorded against the row itself, so the mask has nothing to say. */
    @Test
    void aFileRecordedAgainstNoFieldIsKept() {
        assertEquals(List.of(3L), readable(Set.of("bankStatementFile"), record(3L, null)));
    }

    /**
     * Nothing masked, nothing dropped. Also the admin path: getUserBlockedModelFields returns an empty
     * set for them, and an empty set must mean "no restriction", never "match nothing".
     */
    @Test
    void everyFileIsReturnedWhenNothingIsMasked() {
        assertEquals(List.of(1L, 2L, 3L),
                readable(Set.of(),
                        record(1L, "photoFile"),
                        record(2L, "bankStatementFile"),
                        record(3L, null)));
    }
}
