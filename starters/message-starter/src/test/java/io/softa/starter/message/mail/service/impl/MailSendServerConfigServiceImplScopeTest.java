package io.softa.starter.message.mail.service.impl;

import java.util.Optional;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Scope rules of {@link MailSendServerConfigServiceImpl}: a tenant write
 * addressed at a platform config fails with an explanation, not the ORM's
 * silent no-op. (Platform configs never appear on tenant list surfaces —
 * they are reached only by the dispatcher's default fallback.)
 */
class MailSendServerConfigServiceImplScopeTest {

    private MailSendServerConfigServiceImpl service;
    private MailSendServerConfigService self;

    @BeforeEach
    void setUp() {
        service = spy(new MailSendServerConfigServiceImpl());
        self = mock(MailSendServerConfigService.class);
        ReflectionTestUtils.setField(service, "self", self);
    }

    private static MailSendServerConfig config(long id, Long tenantId) {
        MailSendServerConfig config = new MailSendServerConfig();
        config.setId(id);
        config.setTenantId(tenantId);
        return config;
    }

    @Test
    void writeAddressedAtAPlatformConfigIsRejectedWithAnExplanation() {
        doReturn(Optional.empty()).when(service).getById(9L);
        when(self.findVisibleById(9L)).thenReturn(Optional.of(config(9L, -1L)));

        Assertions.assertThrows(BusinessException.class,
                () -> service.assertWritableInCurrentScope(9L));
    }

    @Test
    void visibleByIdReachesDisabledConfigs() {
        doReturn(Optional.of(config(9L, 5L))).when(service).searchOne(any(FlexQuery.class));

        service.findVisibleById(9L);

        // Replay safety: an accepted record must resolve the config it was
        // accepted with, even after ops disabled that config.
        verify(service).searchOne(argThat((FlexQuery q) ->
                q.getFilterControl().isSkipActiveControl()));
    }

    @Test
    void writeAddressedAtAnOwnConfigPasses() {
        doReturn(Optional.of(new MailSendServerConfig())).when(service).getById(9L);
        Assertions.assertDoesNotThrow(() -> service.assertWritableInCurrentScope(9L));
    }

    @Test
    void writeAddressedAtNothingStaysSilent() {
        doReturn(Optional.empty()).when(service).getById(9L);
        when(self.findVisibleById(9L)).thenReturn(Optional.empty());
        Assertions.assertDoesNotThrow(() -> service.assertWritableInCurrentScope(9L));
    }
}
