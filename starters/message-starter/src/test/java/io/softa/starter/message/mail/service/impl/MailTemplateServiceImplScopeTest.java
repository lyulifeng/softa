package io.softa.starter.message.mail.service.impl;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.service.MailSendServerConfigService;
import io.softa.starter.message.mail.service.MailTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Scope rules of {@link MailTemplateServiceImpl}:
 * <ul>
 *   <li>{@code validatePreferredServerScope} — a template may pin a config of
 *       its own scope, or a platform config shared with tenants;</li>
 *   <li>{@code validateCodeOverride} — a tenant create must not shadow a
 *       locked platform code;</li>
 *   <li>{@code assertWritableInCurrentScope} — a tenant write addressed at a
 *       platform row fails with an explanation, not a silent no-op;</li>
 *   <li>{@code customize} — copy-on-write guards and tenant stamping.</li>
 * </ul>
 */
class MailTemplateServiceImplScopeTest {

    private MailTemplateServiceImpl service;
    private MailTemplateService self;
    private MailSendServerConfigService sendConfigService;

    @BeforeEach
    void setUp() {
        service = spy(new MailTemplateServiceImpl());
        self = mock(MailTemplateService.class);
        sendConfigService = mock(MailSendServerConfigService.class);
        ReflectionTestUtils.setField(service, "self", self);
        ReflectionTestUtils.setField(service, "sendConfigService", sendConfigService);
    }

    @AfterEach
    void tearDown() {
        SystemConfig.env = null;
    }

    /** Run {@code action} as a tenant caller in a multi-tenant deployment. */
    private static void asTenant(long tenantId, Runnable action) {
        SystemConfig config = new SystemConfig();
        config.setEnableMultiTenancy(true);
        SystemConfig.env = config;
        Context ctx = new Context();
        ctx.setTenantId(tenantId);
        ContextHolder.runWith(ctx, action);
    }

    // ------------------------------------------------------------------
    // validatePreferredServerScope
    // ------------------------------------------------------------------

    @Test
    void payloadWithoutTheFieldIsIgnored() {
        service.validatePreferredServerScope(new HashMap<>());
        service.validatePreferredServerScope(null);
        verifyNoInteractions(sendConfigService);
    }

    @Test
    void ownScopeConfigPasses() {
        when(sendConfigService.getById(42L)).thenReturn(Optional.of(new MailSendServerConfig()));

        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", 42);
        Assertions.assertDoesNotThrow(() -> service.validatePreferredServerScope(row));
    }

    @Test
    void sharedPlatformConfigPasses() {
        MailSendServerConfig shared = new MailSendServerConfig();
        shared.setTenantId(0L);
        shared.setSharedWithTenants(true);
        when(sendConfigService.getById(42L)).thenReturn(Optional.empty());
        when(sendConfigService.findVisibleById(42L)).thenReturn(Optional.of(shared));

        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", 42);
        Assertions.assertDoesNotThrow(() -> service.validatePreferredServerScope(row));
    }

    @Test
    void unsharedPlatformConfigIsRejected() {
        MailSendServerConfig internal = new MailSendServerConfig();
        internal.setTenantId(0L);
        internal.setSharedWithTenants(false);
        when(sendConfigService.getById(42L)).thenReturn(Optional.empty());
        when(sendConfigService.findVisibleById(42L)).thenReturn(Optional.of(internal));

        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", 42);
        Assertions.assertThrows(BusinessException.class,
                () -> service.validatePreferredServerScope(row));
    }

    @Test
    void configOutsideTheScopeIsRejected() {
        when(sendConfigService.getById(42L)).thenReturn(Optional.empty());
        when(sendConfigService.findVisibleById(42L)).thenReturn(Optional.empty());

        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", "42");
        Assertions.assertThrows(BusinessException.class,
                () -> service.validatePreferredServerScope(row));
    }

    @Test
    void junkIdIsRejected() {
        Map<String, Object> row = new HashMap<>();
        row.put("preferredServerConfigId", "not-a-number");
        Assertions.assertThrows(BusinessException.class,
                () -> service.validatePreferredServerScope(row));
    }

    // ------------------------------------------------------------------
    // validateCodeOverride
    // ------------------------------------------------------------------

    @Test
    void lockedPlatformCodeCannotBeShadowedByATenantCreate() {
        MailTemplate locked = new MailTemplate();
        locked.setCode("INVOICE_ISSUED");
        locked.setOverridable(false);
        when(self.findPlatformByCodeAny("INVOICE_ISSUED")).thenReturn(Optional.of(locked));

        Map<String, Object> row = new HashMap<>();
        row.put("code", "INVOICE_ISSUED");
        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.validateCodeOverride(row)));
    }

    @Test
    void overridablePlatformCodeMayBeShadowed() {
        MailTemplate open = new MailTemplate();
        open.setCode("USER_WELCOME");
        when(self.findPlatformByCodeAny("USER_WELCOME")).thenReturn(Optional.of(open));

        Map<String, Object> row = new HashMap<>();
        row.put("code", "USER_WELCOME");
        asTenant(5L, () -> Assertions.assertDoesNotThrow(
                () -> service.validateCodeOverride(row)));
    }

    @Test
    void codeOverrideCheckIsSkippedOutsideMultiTenancy() {
        Map<String, Object> row = new HashMap<>();
        row.put("code", "ANY");
        // No SystemConfig.env set → single-tenant behaviour.
        Assertions.assertDoesNotThrow(() -> service.validateCodeOverride(row));
        verifyNoInteractions(self);
    }

    // ------------------------------------------------------------------
    // assertWritableInCurrentScope
    // ------------------------------------------------------------------

    @Test
    void writeAddressedAtAPlatformRowIsRejectedWithAnExplanation() {
        doReturn(Optional.empty()).when(service).getById(9L);
        MailTemplate platform = new MailTemplate();
        platform.setId(9L);
        platform.setTenantId(0L);
        when(self.findPlatformById(9L)).thenReturn(Optional.of(platform));

        Assertions.assertThrows(BusinessException.class,
                () -> service.assertWritableInCurrentScope(9L));
    }

    @Test
    void writeAddressedAtAnOwnRowPasses() {
        doReturn(Optional.of(new MailTemplate())).when(service).getById(9L);
        Assertions.assertDoesNotThrow(() -> service.assertWritableInCurrentScope(9L));
        verifyNoInteractions(self);
    }

    @Test
    void writeAddressedAtNothingStaysSilent() {
        // Not visible anywhere: the normal write path reports its own not-found.
        doReturn(Optional.empty()).when(service).getById(9L);
        when(self.findPlatformById(9L)).thenReturn(Optional.empty());
        Assertions.assertDoesNotThrow(() -> service.assertWritableInCurrentScope(9L));
    }

    // ------------------------------------------------------------------
    // customize
    // ------------------------------------------------------------------

    private MailTemplate platformTemplate(Boolean overridable) {
        MailTemplate platform = new MailTemplate();
        platform.setId(77L);
        platform.setTenantId(0L);
        platform.setCode("USER_WELCOME");
        platform.setName("Welcome");
        platform.setOverridable(overridable);
        return platform;
    }

    @Test
    void customizeIsRejectedOutsideMultiTenancy() {
        Assertions.assertThrows(BusinessException.class, () -> service.customize(77L));
    }

    @Test
    void customizeIsRejectedForThePlatformCaller() {
        asTenant(0L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.customize(77L)));
    }

    @Test
    void customizeIsRejectedForALockedPlatformTemplate() {
        when(self.findPlatformById(77L)).thenReturn(Optional.of(platformTemplate(false)));
        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.customize(77L)));
    }

    @Test
    void customizeIsRejectedWhenTheCodeIsAlreadyCustomized() {
        when(self.findPlatformById(77L)).thenReturn(Optional.of(platformTemplate(null)));
        doReturn(Optional.of(new MailTemplate())).when(service).searchOne(any(Filters.class));
        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.customize(77L)));
    }

    @Test
    void customizeCopiesTheTemplate_andClearsAPinTheTenantMayNotCarry() {
        MailTemplate platform = platformTemplate(null);
        platform.setPreferredServerConfigId(99L);
        when(self.findPlatformById(77L)).thenReturn(Optional.of(platform));
        doReturn(Optional.empty()).when(service).searchOne(any(Filters.class));
        // 99 is a platform-internal config: not own-scope, not shared.
        when(sendConfigService.getById(99L)).thenReturn(Optional.empty());
        when(sendConfigService.findVisibleById(99L)).thenReturn(Optional.empty());
        doReturn(1001L).when(service).createOne(any(MailTemplate.class));

        asTenant(5L, () -> Assertions.assertEquals(1001L, service.customize(77L)));

        ArgumentCaptor<MailTemplate> captor = ArgumentCaptor.forClass(MailTemplate.class);
        verify(service).createOne(captor.capture());
        MailTemplate copy = captor.getValue();
        Assertions.assertEquals("USER_WELCOME", copy.getCode());
        Assertions.assertNull(copy.getId());
        Assertions.assertNull(copy.getPreferredServerConfigId());
    }

    @Test
    void customizeCarriesAPinTheTenantMayUse() {
        MailTemplate platform = platformTemplate(null);
        platform.setPreferredServerConfigId(99L);
        when(self.findPlatformById(77L)).thenReturn(Optional.of(platform));
        doReturn(Optional.empty()).when(service).searchOne(any(Filters.class));
        MailSendServerConfig shared = new MailSendServerConfig();
        shared.setSharedWithTenants(true);
        when(sendConfigService.getById(99L)).thenReturn(Optional.empty());
        when(sendConfigService.findVisibleById(99L)).thenReturn(Optional.of(shared));
        doReturn(1001L).when(service).createOne(any(MailTemplate.class));

        asTenant(5L, () -> service.customize(77L));

        ArgumentCaptor<MailTemplate> captor = ArgumentCaptor.forClass(MailTemplate.class);
        verify(service).createOne(captor.capture());
        Assertions.assertEquals(99L, captor.getValue().getPreferredServerConfigId());
    }
}
