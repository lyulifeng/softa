package io.softa.starter.message.quota;

import java.time.YearMonth;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.message.MessageScope;
import io.softa.starter.message.quota.entity.TenantMessageUsage;
import io.softa.starter.message.quota.service.TenantMessageQuotaService;
import io.softa.starter.message.quota.service.TenantMessageUsageService;
import io.softa.starter.message.shared.MonthlyQuotaGuard;
import io.softa.starter.message.shared.TenantScopes;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static java.util.Optional.of;

/**
 * {@link MonthlyQuotaGuard}: bucket selection follows the send's scope, and
 * the facade hands the CURRENTLY resolved limit (quota row / deployment
 * default) to the ledger's CAS consume — so an operations change takes
 * effect immediately.
 */
class MonthlyQuotaGuardTest {

    private MonthlyQuotaGuard guard;
    private TenantMessageQuotaService quotaService;
    private TenantMessageUsageService usageService;

    @BeforeEach
    void setUp() {
        guard = new MonthlyQuotaGuard();
        quotaService = mock(TenantMessageQuotaService.class);
        usageService = mock(TenantMessageUsageService.class);
        ReflectionTestUtils.setField(guard, "quotaService", quotaService);
        ReflectionTestUtils.setField(guard, "usageService", usageService);
    }

    @Test
    void platformScopeAlwaysDrawsOnThePlatformBucket() {
        Context ctx = new Context();
        ctx.setTenantId(5L);
        ContextHolder.runWith(ctx, () -> Assertions.assertEquals(TenantScopes.PLATFORM,
                MonthlyQuotaGuard.bucketFor(MessageScope.PLATFORM)));
    }

    @Test
    void tenantScopeDrawsOnTheCurrentTenantBucket() {
        Context ctx = new Context();
        ctx.setTenantId(5L);
        ContextHolder.runWith(ctx, () -> Assertions.assertEquals(5L,
                MonthlyQuotaGuard.bucketFor(MessageScope.TENANT)));
    }

    @Test
    void consumeHandsTheChannelsResolvedLimitToTheLedger() {
        when(quotaService.resolveLimits(5L)).thenReturn(
                new TenantMessageQuotaService.ResolvedLimits(100L, 20L));

        guard.consume("mail", 5L);
        verify(usageService).consume("mail", 5L, 100L);

        guard.consume("sms", 5L);
        verify(usageService).consume("sms", 5L, 20L);
    }

    @Test
    void usageReadsTheLedgerRowPerChannel() {
        TenantMessageUsage row = new TenantMessageUsage();
        row.setMailUsed(37L);
        row.setSmsUsed(4L);
        when(usageService.findByBucketAndMonth(5L, YearMonth.of(2026, 8))).thenReturn(of(row));

        Assertions.assertEquals(37L, guard.usage("mail", 5L, YearMonth.of(2026, 8)));
        Assertions.assertEquals(4L, guard.usage("sms", 5L, YearMonth.of(2026, 8)));
    }

    @Test
    void bucketWithNoLedgerRowReadsZero() {
        when(usageService.findByBucketAndMonth(5L, YearMonth.of(2026, 8)))
                .thenReturn(java.util.Optional.empty());
        Assertions.assertEquals(0L, guard.usage("mail", 5L, YearMonth.of(2026, 8)));
    }
}
