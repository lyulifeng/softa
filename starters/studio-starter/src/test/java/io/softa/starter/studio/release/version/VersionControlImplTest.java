package io.softa.starter.studio.release.version;

import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.changelog.message.dto.ChangeLog;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.studio.release.dto.ModelChangesDTO;
import io.softa.starter.studio.release.version.impl.VersionControlImpl;
import io.softa.starter.es.service.ChangeLogService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VersionControlImplTest {

    @Test
    void collectModelChangesUsesSingleBulkQueryAcrossVersionedModels() {
        VersionControlImpl versionControl = new VersionControlImpl();
        ChangeLogService changeLogService = mock(ChangeLogService.class);
        @SuppressWarnings("unchecked")
        ModelService<Long> modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(versionControl, "changeLogService", changeLogService);
        ReflectionTestUtils.setField(versionControl, "modelService", modelService);

        ChangeLog modelDelete = deleteLog("DesignModel", 101L, Map.of("modelName", "Account"));
        ChangeLog fieldDelete = deleteLog("DesignField", 202L, Map.of("modelName", "Account", "fieldName", "name"));
        when(changeLogService.searchByCorrelationIds(anyList(), eq(List.of("11", "12"))))
                .thenReturn(List.of(modelDelete, fieldDelete));

        List<ModelChangesDTO> result = versionControl.collectModelChanges(1L, List.of(11L, 12L));

        assertEquals(2, result.size());
        assertEquals(List.of("DesignModel", "DesignField"), result.stream().map(ModelChangesDTO::getModelName).toList());
        assertTrue(result.stream().allMatch(dto -> dto.getDeletedRows().size() == 1));
        verify(changeLogService).searchByCorrelationIds(anyList(), eq(List.of("11", "12")));
    }

    private ChangeLog deleteLog(String model, Long rowId, Map<String, Object> dataBeforeChange) {
        ChangeLog changeLog = new ChangeLog();
        changeLog.setModel(model);
        changeLog.setRowId(rowId);
        changeLog.setAccessType(AccessType.DELETE);
        changeLog.setDataBeforeChange(dataBeforeChange);
        changeLog.setChangedById(1L);
        changeLog.setChangedBy("tester");
        changeLog.setChangedTime("2026-03-23T20:00:00");
        return changeLog;
    }
}
