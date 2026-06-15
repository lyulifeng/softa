package io.softa.framework.orm.service.impl;

import java.io.Serializable;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.meta.ModelManager;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The copy entry points must reject models declared {@code @Model(copyable = false)}
 * before any read or write happens.
 */
class ModelServiceCopyRejectionTest {

    @BeforeAll
    static void ensureSystemConfig() {
        if (SystemConfig.env == null) {
            SystemConfig.env = new SystemConfig();
        }
    }

    @Test
    void copyByIds_rejectsNonCopyableModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> mock = Mockito.mockStatic(ModelManager.class)) {
            mock.when(() -> ModelManager.isCopyableModel("AuditLog")).thenReturn(false);

            IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                    () -> service.copyByIds("AuditLog", List.of(1L)));
            assertTrue(ex.getMessage().contains("not copyable"));
        }
    }

    @Test
    void getCopyableFields_rejectsNonCopyableModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> mock = Mockito.mockStatic(ModelManager.class)) {
            mock.when(() -> ModelManager.isCopyableModel("AuditLog")).thenReturn(false);

            assertThrows(IllegalArgumentException.class,
                    () -> service.getCopyableFields("AuditLog", 1L));
        }
    }
}
