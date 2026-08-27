package io.softa.starter.message.mail.service.impl;

import java.util.List;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.service.MailSendServerConfigService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Scope rules of {@link MailSendServerConfigServiceImpl}: what
 * {@code listSelectable} exposes to tenant vs platform callers, and the
 * explanatory rejection of tenant writes addressed at platform rows.
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

    @AfterEach
    void tearDown() {
        SystemConfig.env = null;
    }

    private static void asTenant(long tenantId, Runnable action) {
        SystemConfig config = new SystemConfig();
        config.setEnableMultiTenancy(true);
        SystemConfig.env = config;
        Context ctx = new Context();
        ctx.setTenantId(tenantId);
        ContextHolder.runWith(ctx, action);
    }

    private static MailSendServerConfig config(long id, Long tenantId, Boolean shared, Integer sequence) {
        MailSendServerConfig config = new MailSendServerConfig();
        config.setId(id);
        config.setTenantId(tenantId);
        config.setSharedWithTenants(shared);
        config.setSequence(sequence);
        config.setIsEnabled(true);
        return config;
    }

    @Test
    void tenantSeesOwnConfigsAndSharedPlatformConfigsOnly() {
        MailSendServerConfig own = config(1L, 5L, null, 20);
        MailSendServerConfig shared = config(2L, 0L, true, 10);
        MailSendServerConfig internal = config(3L, 0L, false, 1);
        doReturn(List.of(own, shared, internal)).when(service).searchList(any(FlexQuery.class));

        asTenant(5L, () -> {
            List<MailSendServerConfig> selectable = service.listSelectable();
            Assertions.assertEquals(List.of(2L, 1L),
                    selectable.stream().map(MailSendServerConfig::getId).toList(),
                    "shared platform config and own config, ordered by sequence; "
                            + "platform-internal config invisible");
        });
    }

    @Test
    void platformCallerSeesItsOwnConfigsRegardlessOfSharing() {
        MailSendServerConfig internal = config(3L, 0L, false, 1);
        doReturn(List.of(internal)).when(service).searchList(any(FlexQuery.class));

        asTenant(0L, () -> Assertions.assertEquals(1, service.listSelectable().size()));
    }

    @Test
    void writeAddressedAtAPlatformConfigIsRejectedWithAnExplanation() {
        doReturn(Optional.empty()).when(service).getById(9L);
        when(self.findVisibleById(9L)).thenReturn(Optional.of(config(9L, 0L, true, 1)));

        Assertions.assertThrows(BusinessException.class,
                () -> service.assertWritableInCurrentScope(9L));
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
