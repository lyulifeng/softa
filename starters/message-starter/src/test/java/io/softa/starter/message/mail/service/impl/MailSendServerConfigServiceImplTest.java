package io.softa.starter.message.mail.service.impl;

import java.util.List;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.message.mail.entity.MailSendServerConfig;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

class MailSendServerConfigServiceImplTest {

    private static MailSendServerConfig config(long id) {
        MailSendServerConfig config = new MailSendServerConfig();
        config.setId(id);
        config.setIsDefault(true);
        return config;
    }

    @Test
    void demoteClearsEveryOtherDefaultButKeepsTheSavedOne() {
        MailSendServerConfigServiceImpl service = spy(new MailSendServerConfigServiceImpl());
        doReturn(List.of(config(1L), config(2L), config(3L)))
                .when(service).searchList(any(Filters.class));
        doReturn(true).when(service).updateOne(any(MailSendServerConfig.class));

        service.demoteOtherDefaults(2L);

        verify(service, times(2)).updateOne(any(MailSendServerConfig.class));
        verify(service).updateOne(argThat((MailSendServerConfig c) ->
                c.getId().equals(1L) && Boolean.FALSE.equals(c.getIsDefault())));
        verify(service).updateOne(argThat((MailSendServerConfig c) ->
                c.getId().equals(3L) && Boolean.FALSE.equals(c.getIsDefault())));
    }

    @Test
    void demoteIsNoOpWhenTheSavedRowIsTheOnlyDefault() {
        MailSendServerConfigServiceImpl service = spy(new MailSendServerConfigServiceImpl());
        doReturn(List.of(config(2L))).when(service).searchList(any(Filters.class));

        service.demoteOtherDefaults(2L);

        verify(service, never()).updateOne(any(MailSendServerConfig.class));
    }
}
