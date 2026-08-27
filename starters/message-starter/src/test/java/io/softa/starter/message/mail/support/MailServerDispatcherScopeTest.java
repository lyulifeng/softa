package io.softa.starter.message.mail.support;

import java.util.Optional;
import java.util.function.Supplier;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MessageScope;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tier policy of {@link MailServerDispatcher#resolveSend(MessageScope)}:
 * {@code PLATFORM} must neither consult the tenant default nor touch the
 * tenant's default cache key; {@code TENANT} keeps the silent platform
 * fallback when the tenant has no default of its own.
 */
class MailServerDispatcherScopeTest {

    private MailServerDispatcher dispatcher;
    private MailSendServerConfigService sendConfigService;
    private MailConfigCache configCache;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dispatcher = new MailServerDispatcher();
        sendConfigService = mock(MailSendServerConfigService.class);
        configCache = mock(MailConfigCache.class);
        ReflectionTestUtils.setField(dispatcher, "sendConfigService", sendConfigService);
        ReflectionTestUtils.setField(dispatcher, "configCache", configCache);
        // Pass-through cache: exercise the loader supplied by the dispatcher.
        when(configCache.getDefault(any()))
                .thenAnswer(inv -> ((Supplier<MailSendServerConfig>) inv.getArgument(0)).get());
        when(configCache.getPlatformDefault(any()))
                .thenAnswer(inv -> ((Supplier<MailSendServerConfig>) inv.getArgument(0)).get());
    }

    private static MailSendServerConfig config(long id) {
        MailSendServerConfig config = new MailSendServerConfig();
        config.setId(id);
        return config;
    }

    @Test
    void tenantScopePrefersTheTenantDefault() {
        when(sendConfigService.findTenantDefault()).thenReturn(Optional.of(config(1L)));

        Assertions.assertEquals(1L, dispatcher.resolveSend(MessageScope.TENANT).getId());
        verify(sendConfigService, never()).findPlatformDefault();
        verify(configCache, never()).getPlatformDefault(any());
    }

    @Test
    void tenantScopeFallsBackToThePlatformDefault() {
        when(sendConfigService.findTenantDefault()).thenReturn(Optional.empty());
        when(sendConfigService.findPlatformDefault()).thenReturn(Optional.of(config(2L)));

        Assertions.assertEquals(2L, dispatcher.resolveSend(MessageScope.TENANT).getId());
    }

    @Test
    void platformScopeNeverConsultsTheTenantDefault() {
        when(sendConfigService.findPlatformDefault()).thenReturn(Optional.of(config(2L)));

        Assertions.assertEquals(2L, dispatcher.resolveSend(MessageScope.PLATFORM).getId());
        verify(sendConfigService, never()).findTenantDefault();
        // ...and never reads or writes the tenant's default cache entry.
        verify(configCache, never()).getDefault(any());
    }

    @Test
    void platformScopeWithNoPlatformDefaultFailsLoud() {
        when(sendConfigService.findPlatformDefault()).thenReturn(Optional.empty());

        BusinessException ex = Assertions.assertThrows(BusinessException.class,
                () -> dispatcher.resolveSend(MessageScope.PLATFORM));
        Assertions.assertTrue(ex.getMessage().contains("platform"));
    }

    @Test
    void noArgResolveSendDefaultsToTenantScope() {
        when(sendConfigService.findTenantDefault()).thenReturn(Optional.of(config(1L)));
        Assertions.assertEquals(1L, dispatcher.resolveSend().getId());
    }
}
