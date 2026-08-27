package io.softa.starter.message.mail.service.impl;

import java.util.HashMap;
import java.util.Map;
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
import io.softa.framework.base.message.MessageScope;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.starter.message.mail.entity.MailSendServerConfig;
import io.softa.starter.message.mail.entity.MailTemplate;
import io.softa.starter.message.mail.service.MailSendServerConfigService;
import io.softa.starter.message.mail.service.MailTemplateService;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tier rules of {@link MailTemplateServiceImpl}:
 * <ul>
 *   <li>{@code resolve} — TENANT reads the current scope's own rows,
 *       PLATFORM explicitly targets the platform tier; no cross-tier
 *       fallback in either direction;</li>
 *   <li>{@code validatePreferredServerScope} — a template may only pin a
 *       config of its own scope (platform configs are invisible);</li>
 *   <li>{@code assertWritableInCurrentScope} — a tenant write addressed at a
 *       platform row fails with an explanation, not a silent no-op.</li>
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
    // resolve — tier-pure, no fallback
    // ------------------------------------------------------------------

    @Test
    void tenantScopeResolvesOwnRowsOnly_neverFallsBackToPlatform() {
        doReturn(Optional.empty()).when(service).searchOne(any(Filters.class));

        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.resolve("USER_WELCOME", MessageScope.TENANT)));
        // The platform tier is never consulted for a TENANT-scoped send.
        verify(self, never()).findPlatformByCode(any());
    }

    @Test
    void platformScopeResolvesThePlatformTierOnly() {
        MailTemplate platform = new MailTemplate();
        platform.setCode("INVOICE_ISSUED");
        when(self.findPlatformByCode("INVOICE_ISSUED")).thenReturn(Optional.of(platform));

        asTenant(5L, () -> Assertions.assertSame(platform,
                service.resolve("INVOICE_ISSUED", MessageScope.PLATFORM)));
        // The tenant's own rows are never consulted for a PLATFORM-scoped send.
        verify(service, never()).searchOne(any(Filters.class));
    }

    @Test
    void platformScopeMissingTemplateFailsLoud_neverFallsBackToTenant() {
        when(self.findPlatformByCode("INVOICE_ISSUED")).thenReturn(Optional.empty());

        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.resolve("INVOICE_ISSUED", MessageScope.PLATFORM)));
        verify(service, never()).searchOne(any(Filters.class));
    }

    @Test
    void singleTenantDeploymentIgnoresTheScope() {
        // No SystemConfig.env set → multi-tenancy off → one namespace.
        MailTemplate only = new MailTemplate();
        doReturn(Optional.of(only)).when(service).searchOne(any(Filters.class));

        Assertions.assertSame(only, service.resolve("ANY", MessageScope.PLATFORM));
        verifyNoInteractions(self);
    }

    // ------------------------------------------------------------------
    // active control — where disabled rows must stay reachable
    // ------------------------------------------------------------------

    @Test
    void resolveAppliesTheFrameworkActiveFilter_noExplicitEnabledCondition() {
        doReturn(Optional.of(new MailTemplate())).when(service).searchOne(any(Filters.class));

        asTenant(5L, () -> service.resolve("USER_WELCOME", MessageScope.TENANT));

        // The model is activeControl: `active = true` is appended by
        // WhereBuilder, so the service must NOT hand-roll the condition (a
        // plain Filters read is exactly what proves it).
        verify(service).searchOne(any(Filters.class));
    }

    @Test
    void resolveAnyReachesDisabledRows() {
        doReturn(Optional.of(new MailTemplate())).when(service).searchOne(any(FlexQuery.class));

        service.resolveAny("USER_WELCOME");

        // Authoring tools must inspect a template BEFORE it is activated.
        verify(service).searchOne(argThat((FlexQuery q) ->
                q.getFilterControl().isSkipActiveControl()));
    }

    @Test
    void platformRowWriteProbeReachesDisabledRows() {
        doReturn(Optional.empty()).when(service).getById(9L);
        doReturn(Optional.of(new MailTemplate())).when(service).searchOne(any(FlexQuery.class));
        // Called on the impl directly (not through `self`) to exercise the query.
        service.findPlatformById(9L);

        // A DISABLED platform row must still be recognised, else the
        // explanatory rejection degrades back to a silent no-op.
        verify(service).searchOne(argThat((FlexQuery q) ->
                q.getFilterControl().isSkipActiveControl()));
    }

    // ------------------------------------------------------------------
    // validatePreferredServerScope — own scope only
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
    void configOutsideTheScopeIsRejected_platformConfigsIncluded() {
        // Platform configs are invisible to tenants: the own-scope read misses
        // them, so pinning one is rejected exactly like any foreign config.
        when(sendConfigService.getById(42L)).thenReturn(Optional.empty());

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
    // assertWritableInCurrentScope
    // ------------------------------------------------------------------

    @Test
    void writeAddressedAtAPlatformRowIsRejectedWithAnExplanation() {
        doReturn(Optional.empty()).when(service).getById(9L);
        MailTemplate platform = new MailTemplate();
        platform.setId(9L);
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
}
