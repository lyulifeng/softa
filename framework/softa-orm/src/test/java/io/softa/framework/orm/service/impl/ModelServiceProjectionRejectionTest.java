package io.softa.framework.orm.service.impl;

import java.io.Serializable;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

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
 * A projection model ({@code @Model(projection = true)}) is read-only: its rows live in a
 * table owned by another model or an external process. Every write root —
 * {@code createList}, {@code updateList}, {@code deleteByIds}, {@code deleteBySliceId},
 * {@code setEndDate} — must reject it before any permission check or DB work, and each
 * root covers the entry points that funnel into it (createOne, updateByFilter,
 * deleteByBusinessKey, copy*, …).
 */
class ModelServiceProjectionRejectionTest {

    private static final String MODEL = "BirthdayCountdown";

    @BeforeAll
    static void ensureSystemConfig() {
        if (SystemConfig.env == null) {
            SystemConfig.env = new SystemConfig();
        }
    }

    private static MockedStatic<ModelManager> projectionModelManager() {
        MockedStatic<ModelManager> mock = Mockito.mockStatic(ModelManager.class);
        mock.when(() -> ModelManager.isProjectionModel(MODEL)).thenReturn(true);
        return mock;
    }

    private static void assertRejected(org.junit.jupiter.api.function.Executable write) {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, write);
        assertTrue(ex.getMessage().contains("read-only projection"), ex.getMessage());
    }

    @Test
    void createList_rejectsProjectionModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> ignored = projectionModelManager()) {
            // Mutable list: Assert.allNotNull probes contains(null), which List.of rejects.
            assertRejected(() -> service.createList(MODEL, new ArrayList<>(List.of(Map.of("fullName", "A")))));
        }
    }

    @Test
    void updateList_rejectsProjectionModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> ignored = projectionModelManager()) {
            assertRejected(() -> service.updateList(MODEL, List.of(Map.of("id", 1L, "fullName", "A"))));
        }
    }

    @Test
    void deleteByIds_rejectsProjectionModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> ignored = projectionModelManager()) {
            assertRejected(() -> service.deleteByIds(MODEL, new ArrayList<>(List.of(1L))));
        }
    }

    @Test
    void deleteBySliceId_rejectsProjectionModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> ignored = projectionModelManager()) {
            assertRejected(() -> service.deleteBySliceId(MODEL, 11L));
        }
    }

    @Test
    void setEndDate_rejectsProjectionModel() {
        ModelServiceImpl<Serializable> service = new ModelServiceImpl<>();
        try (MockedStatic<ModelManager> ignored = projectionModelManager()) {
            assertRejected(() -> service.setEndDate(MODEL, 1L, LocalDate.of(2026, 1, 1)));
        }
    }
}
