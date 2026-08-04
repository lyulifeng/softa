package io.softa.starter.studio.meta.support;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.studio.meta.entity.DesignField;
import io.softa.starter.studio.meta.entity.DesignModel;
import io.softa.starter.studio.meta.service.DesignFieldService;
import io.softa.starter.studio.meta.service.DesignModelService;

/**
 * Studio-lane counterpart of the {@code AnnotationParser} version-default materialization: a
 * {@code version} field written under a versionLock model has its starting value stamped into
 * {@code design_field.default_value}, so the deployed {@code sys_field} (and the rendered DDL)
 * always carries it. An authored default wins; non-version fields and non-versionLock models are
 * left untouched.
 */
class DesignVersionDefaultStamperTest {

    private static DesignVersionDefaultStamper stamper(DesignFieldService fieldService,
                                                       DesignModelService modelService) {
        DesignVersionDefaultStamper stamper = new DesignVersionDefaultStamper();
        ReflectionTestUtils.setField(stamper, "fieldService", fieldService);
        ReflectionTestUtils.setField(stamper, "modelService", modelService);
        return stamper;
    }

    private static DesignModel model(boolean versionLock) {
        DesignModel model = new DesignModel();
        model.setVersionLock(versionLock);
        return model;
    }

    @Test
    void stampsStartingDefaultOnVersionFieldOfVersionLockModel() {
        DesignFieldService fieldService = mock(DesignFieldService.class);
        DesignModelService modelService = mock(DesignModelService.class);
        when(modelService.searchOne(any(FlexQuery.class))).thenReturn(Optional.of(model(true)));

        Map<String, Object> row = new HashMap<>();
        row.put("appId", 100L);
        row.put("envId", 2L);
        row.put("modelName", "MailSendRecord");
        row.put("fieldName", "version");

        stamper(fieldService, modelService).stamp(row);

        assertEquals("0", row.get("defaultValue"));
    }

    @Test
    void authoredDefaultIsLeftUntouched() {
        DesignFieldService fieldService = mock(DesignFieldService.class);
        DesignModelService modelService = mock(DesignModelService.class);

        Map<String, Object> row = new HashMap<>();
        row.put("appId", 100L);
        row.put("envId", 2L);
        row.put("modelName", "MailSendRecord");
        row.put("fieldName", "version");
        row.put("defaultValue", "100");

        stamper(fieldService, modelService).stamp(row);

        assertEquals("100", row.get("defaultValue"));
        verify(modelService, never()).searchOne(any(FlexQuery.class));
    }

    @Test
    void nonVersionLockModelIsLeftUntouched() {
        DesignFieldService fieldService = mock(DesignFieldService.class);
        DesignModelService modelService = mock(DesignModelService.class);
        when(modelService.searchOne(any(FlexQuery.class))).thenReturn(Optional.of(model(false)));

        Map<String, Object> row = new HashMap<>();
        row.put("appId", 100L);
        row.put("envId", 2L);
        row.put("modelName", "MailTemplate");
        row.put("fieldName", "version");

        stamper(fieldService, modelService).stamp(row);

        assertFalse(row.containsKey("defaultValue"));
    }

    @Test
    void nonVersionFieldShortCircuits() {
        DesignFieldService fieldService = mock(DesignFieldService.class);
        DesignModelService modelService = mock(DesignModelService.class);

        Map<String, Object> row = new HashMap<>();
        row.put("appId", 100L);
        row.put("envId", 2L);
        row.put("modelName", "MailSendRecord");
        row.put("fieldName", "status");

        stamper(fieldService, modelService).stamp(row);

        assertFalse(row.containsKey("defaultValue"));
        verify(modelService, never()).searchOne(any(FlexQuery.class));
    }

    @Test
    void partialUpdateBackfillsFromPersistedRow() {
        DesignFieldService fieldService = mock(DesignFieldService.class);
        DesignModelService modelService = mock(DesignModelService.class);

        // Persisted version field with no default (authored before versionLock was enabled on the
        // model). The client re-saves the field without touching fieldName/defaultValue — the stamp
        // re-resolves from the persisted row and heals the missing starting value.
        DesignField existing = new DesignField();
        existing.setId(5L);
        existing.setFieldName("version");
        existing.setModelName("MailSendRecord");
        existing.setAppId(100L);
        existing.setEnvId(2L);
        when(fieldService.getById(5L)).thenReturn(Optional.of(existing));
        when(modelService.searchOne(any(FlexQuery.class))).thenReturn(Optional.of(model(true)));

        Map<String, Object> row = new HashMap<>();
        row.put("id", 5L);
        row.put("description", "bump");

        stamper(fieldService, modelService).stamp(row);

        assertEquals("0", row.get("defaultValue"));
    }
}
