package io.softa.starter.message.mail.controller;

import java.util.HashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.service.ModelService;
import io.softa.starter.message.mail.service.MailReceiveServerConfigService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class MailReceiveServerConfigControllerTest {

    private MailReceiveServerConfigController controller;
    private MailReceiveServerConfigService service;
    private ModelService<Long> modelService;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        controller = new MailReceiveServerConfigController();
        service = mock(MailReceiveServerConfigService.class);
        modelService = mock(ModelService.class);
        ReflectionTestUtils.setField(controller, "service", service);
        ReflectionTestUtils.setField(controller, "modelService", modelService);
    }

    @Test
    void createMarkedDefaultDemotesOthers() {
        when(modelService.createOne(eq("MailReceiveServerConfig"), any())).thenReturn(3L);

        Map<String, Object> row = new HashMap<>();
        row.put("isDefault", true);
        controller.createOne(row);

        verify(service).demoteOtherDefaults(3L);
    }

    @Test
    void updateWithoutDefaultDoesNotDemote() {
        when(modelService.updateOne(eq("MailReceiveServerConfig"), any())).thenReturn(true);

        Map<String, Object> row = new HashMap<>();
        row.put("id", 4);
        controller.updateOne(row);

        verify(service, never()).demoteOtherDefaults(any());
    }
}
