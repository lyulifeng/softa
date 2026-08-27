package io.softa.starter.message.mail.controller;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.ModelService;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailSendServerConfigControllerTest {

    private MailSendServerConfigController controller;
    private MailSendServerConfigService service;
    private ModelService<Long> modelService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new MailSendServerConfigController();
        service = mock(MailSendServerConfigService.class);
        modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "modelService", modelService);
    }

    @Test
    void createMarkedDefaultDemotesOthers() {
        when(modelService.createOne(eq("MailSendServerConfig"), any())).thenReturn(7L);

        Map<String, Object> row = new HashMap<>();
        row.put("isDefault", true);
        Long id = controller.createOne(row).getData();

        assertEquals(7L, id);
        verify(service).demoteOtherDefaults(7L);
    }

    @Test
    void createWithoutDefaultDoesNotDemote() {
        when(modelService.createOne(eq("MailSendServerConfig"), any())).thenReturn(8L);

        controller.createOne(new HashMap<>());

        verify(service, never()).demoteOtherDefaults(any());
    }

    @Test
    void updateMarkedDefaultDemotesOthersByRowId() {
        when(modelService.updateOne(eq("MailSendServerConfig"), any())).thenReturn(true);

        Map<String, Object> row = new HashMap<>();
        row.put("id", 5);
        row.put("isDefault", "true");
        controller.updateOne(row);

        verify(service).demoteOtherDefaults(5L);
    }

    @Test
    void updateTurningDefaultOffDoesNotDemote() {
        when(modelService.updateOne(eq("MailSendServerConfig"), any())).thenReturn(true);

        Map<String, Object> row = new HashMap<>();
        row.put("id", 5);
        row.put("isDefault", false);
        controller.updateOne(row);

        verify(service, never()).demoteOtherDefaults(any());
    }
}
