package io.softa.starter.message.mail.controller;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.message.mail.service.MailTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class MailTemplateControllerTest {

    private MailTemplateController controller;
    private MailTemplateService service;
    private ModelService<Long> modelService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new MailTemplateController();
        service = mock(MailTemplateService.class);
        modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "modelService", modelService);
    }

    @Test
    void createValidatesServerScopeBeforeWriting() {
        when(modelService.createOne(eq("MailTemplate"), any())).thenReturn(11L);

        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", 42);
        controller.createOne(row);

        verify(service).validatePreferredServerScope(row);
        verify(modelService).createOne(eq("MailTemplate"), any());
    }

    @Test
    void scopeViolationRejectsTheWriteEntirely() {
        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", 42);
        doThrow(new BusinessException("out of scope"))
                .when(service).validatePreferredServerScope(row);

        Assertions.assertThrows(BusinessException.class, () -> controller.updateOne(row));
        verifyNoInteractions(modelService);
    }

    @Test
    void createRejectsALockedCodeOverrideBeforeWriting() {
        Map<String, Object> row = new HashMap<>();
        row.put("code", "INVOICE_ISSUED");
        doThrow(new BusinessException("locked platform code"))
                .when(service).validateCodeOverride(row);

        Assertions.assertThrows(BusinessException.class, () -> controller.createOne(row));
        verifyNoInteractions(modelService);
    }

    @Test
    void updateProbesThePlatformRowGuardWithThePayloadId() {
        Map<String, Object> row = new HashMap<>();
        row.put("id", 9L);
        doThrow(new BusinessException("platform-owned"))
                .when(service).assertWritableInCurrentScope(9L);

        Assertions.assertThrows(BusinessException.class, () -> controller.updateOne(row));
        verifyNoInteractions(modelService);
    }

    @Test
    void customizeDelegatesToTheService() {
        when(service.customize(77L)).thenReturn(1001L);
        Assertions.assertEquals(1001L, controller.customize(77L).getData());
    }

    @Test
    void deleteByIdRoutesThroughTheServiceForTheScopeGuard() {
        when(service.deleteById(9L)).thenReturn(true);
        Assertions.assertTrue(controller.deleteById(9L).getData());
        verify(service).deleteById(9L);
        verifyNoInteractions(modelService);
    }
}
