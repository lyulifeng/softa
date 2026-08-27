package io.softa.starter.message.quota;

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
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.message.config.MessageProperties;
import io.softa.starter.message.quota.entity.TenantMessageQuota;
import io.softa.starter.message.quota.service.TenantMessageQuotaService;
import io.softa.starter.message.quota.service.impl.TenantMessageQuotaServiceImpl;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * {@link TenantMessageQuotaServiceImpl}: limit resolution falls back per
 * field to the deployment defaults, and every write path is platform-only.
 */
class TenantMessageQuotaServiceImplTest {

    private TenantMessageQuotaServiceImpl service;
    private MessageProperties properties;

    @BeforeEach
    void setUp() {
        service = spy(new TenantMessageQuotaServiceImpl());
        properties = new MessageProperties();
        ReflectionTestUtils.setField(service, "messageProperties", properties);
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

    @Test
    void rowLimitsWinOverDefaults() {
        properties.getQuota().setMailMonthlyDefault(1000L);
        TenantMessageQuota row = new TenantMessageQuota();
        row.setMailMonthlyLimit(50L);
        doReturn(Optional.of(row)).when(service).searchOne(any(Filters.class));

        TenantMessageQuotaService.ResolvedLimits limits = service.resolveLimits(5L);
        Assertions.assertEquals(50L, limits.mailMonthlyLimit());
        // No SMS limit on the row → falls back to the (unset) default.
        Assertions.assertNull(limits.smsMonthlyLimit());
    }

    @Test
    void missingRowFallsBackToDeploymentDefaults() {
        properties.getQuota().setMailMonthlyDefault(1000L);
        properties.getQuota().setSmsMonthlyDefault(200L);
        doReturn(Optional.empty()).when(service).searchOne(any(Filters.class));

        TenantMessageQuotaService.ResolvedLimits limits = service.resolveLimits(5L);
        Assertions.assertEquals(1000L, limits.mailMonthlyLimit());
        Assertions.assertEquals(200L, limits.smsMonthlyLimit());
    }

    @Test
    void nothingConfiguredMeansUnlimited() {
        doReturn(Optional.empty()).when(service).searchOne(any(Filters.class));

        TenantMessageQuotaService.ResolvedLimits limits = service.resolveLimits(5L);
        Assertions.assertNull(limits.mailMonthlyLimit());
        Assertions.assertNull(limits.smsMonthlyLimit());
    }

    @Test
    void tenantScopeCannotWriteQuota() {
        asTenant(5L, () -> Assertions.assertThrows(BusinessException.class,
                () -> service.assertPlatformScope()));
    }

    @Test
    void platformScopeMayWriteQuota() {
        asTenant(-1L, () -> Assertions.assertDoesNotThrow(() -> service.assertPlatformScope()));
    }

    @Test
    void singleTenantDeploymentIsUnrestricted() {
        // No SystemConfig.env → multi-tenancy off → no platform gate.
        Assertions.assertDoesNotThrow(() -> service.assertPlatformScope());
    }
}
