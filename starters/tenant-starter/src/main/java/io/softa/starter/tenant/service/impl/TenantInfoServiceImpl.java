package io.softa.starter.tenant.service.impl;

import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import tools.jackson.core.type.TypeReference;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.orm.jdbc.JdbcService;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.enums.TenantProvisioningStatus;
import io.softa.starter.tenant.enums.TenantStatus;

/**
 * Default implementation of the framework {@link TenantInfoService} SPI, living in
 * tenant-starter alongside the {@link TenantInfo} entity. The framework SPI exposes only
 * non-entity methods (active ids / isTenantActive / deactivate); the entity-returning
 * {@code getTenantInfo} / {@code getActiveTenantList} are internal helpers here.
 */
@Slf4j
@Component
public class TenantInfoServiceImpl extends EntityServiceImpl<TenantInfo, Long> implements TenantInfoService {

    /**
     * The platform tenant's own row ({@code tenant_info.id = -1}). It exists because the runtime rejects
     * any account whose tenant is not active — including the platform admins' — so suspending or closing
     * it would lock them out with no way back in.
     */
    private static final Long PLATFORM_TENANT_ID = -1L;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private JdbcService<?> jdbcService;

    @Override
    public List<Long> getActiveTenantIds() {
        // Try Redis cache first
        List<Long> tenantIds = cacheService.get(RedisConstant.TENANT_IDS, new TypeReference<>() {});
        if (tenantIds != null && !tenantIds.isEmpty()) {
            return tenantIds;
        }
        // Fallback to database query
        List<TenantInfo> tenants = getActiveTenantList();
        tenantIds = tenants.stream().map(TenantInfo::getId).toList();
        if (!tenantIds.isEmpty()) {
            cacheService.save(RedisConstant.TENANT_IDS, tenantIds, RedisConstant.ONE_MONTH);
        }
        return tenantIds;
    }

    @Override
    public boolean isTenantActive(Long tenantId) {
        if (tenantId == null) {
            return false;
        }
        TenantInfo tenantInfo = getTenantInfo(tenantId);
        return tenantInfo != null && TenantStatus.ACTIVE.equals(tenantInfo.getStatus());
    }

    @Override
    public void deactivate(Long tenantId) {
        transitionTo(tenantId, TenantStatus.SUSPENDED, TenantStatus.ACTIVE);
    }

    @Override
    public void activate(Long tenantId) {
        transitionTo(tenantId, TenantStatus.ACTIVE, TenantStatus.SUSPENDED);
    }

    @Override
    public void close(Long tenantId) {
        transitionTo(tenantId, TenantStatus.CLOSED, TenantStatus.ACTIVE, TenantStatus.SUSPENDED);
    }

    /**
     * The one write path for {@link TenantStatus}: check the transition is legal, stamp the timestamp
     * belonging to the target status and clear the other two, then evict the tenant caches.
     *
     * <p>Exactly one timestamp is ever set, which keeps the trio a function of the status — an ACTIVE
     * tenant can never display a suspended time. Round-trip history (how often, how long) is the change
     * log's job; three flat columns could not hold it anyway.
     *
     * <p>The eviction is the part that cannot be skipped: {@code isTenantActive()} reads through the
     * cache, so without it a suspension does not take effect until the entry expires and the tenant's
     * users keep working meanwhile.
     */
    private void transitionTo(Long tenantId, TenantStatus target, TenantStatus... allowedFrom) {
        // Suspending or closing the platform tenant would lock the platform admins out permanently —
        // they could not log in to undo it, and there is no other path back.
        Assert.notTrue(PLATFORM_TENANT_ID.equals(tenantId),
                "The platform tenant's operational status cannot be changed.");
        TenantInfo tenant = this.getById(tenantId).orElse(null);
        Assert.notNull(tenant, "Tenant not found for tenantId: {0}", tenantId);
        if (target.equals(tenant.getStatus())) {
            return;   // idempotent — already there, nothing to write or evict
        }
        Assert.isTrue(Arrays.asList(allowedFrom).contains(tenant.getStatus()),
                "Tenant {0} cannot move from {1} to {2}.", tenantId, tenant.getStatus(), target);

        TenantStatus previous = tenant.getStatus();
        LocalDateTime now = LocalDateTime.now();
        tenant.setActivatedTime(target == TenantStatus.ACTIVE ? now : null);
        tenant.setSuspendedTime(target == TenantStatus.SUSPENDED ? now : null);
        tenant.setClosedTime(target == TenantStatus.CLOSED ? now : null);
        tenant.setStatus(target);
        this.updateOne(tenant);

        cacheService.clear(RedisConstant.TENANT_INFO + tenantId);
        cacheService.clear(RedisConstant.TENANT_IDS);
        log.info("Tenant {} status {} -> {}", tenantId, previous, target);
    }

    /**
     * Write the tenant's {@link TenantProvisioningStatus} (seed-progress axis; separate from operational
     * status). Idempotent — a no-op when already at the target, so it is safe under MQ redelivery and
     * concurrent seeders. Evicts the cached TenantInfo so readers see it; login is unaffected (it keys off
     * {@code status == ACTIVE}, not this axis).
     */
    public void markProvisioningStatus(Long tenantId, TenantProvisioningStatus status) {
        TenantInfo tenant = this.getById(tenantId).orElse(null);
        Assert.notNull(tenant, "Tenant not found for tenantId: {0}", tenantId);
        if (status.equals(tenant.getProvisioningStatus())) {
            return;
        }
        tenant.setProvisioningStatus(status);
        this.updateOne(tenant);
        cacheService.clear(RedisConstant.TENANT_INFO + tenantId);
    }

    /** Cached single-tenant lookup — internal to tenant-starter (not part of the framework SPI). */
    public TenantInfo getTenantInfo(Long tenantId) {
        TenantInfo tenantInfo = cacheService.get(RedisConstant.TENANT_INFO + tenantId, TenantInfo.class);
        if (tenantInfo != null) {
            return tenantInfo;
        }
        tenantInfo = this.getById(tenantId).orElse(null);
        if (tenantInfo != null) {
            cacheService.save(RedisConstant.TENANT_INFO + tenantId, tenantInfo);
        }
        return tenantInfo;
    }

    /** All ACTIVE tenants — internal helper feeding {@link #getActiveTenantIds()}. */
    public List<TenantInfo> getActiveTenantList() {
        return jdbcService.selectMetaEntityList(TenantInfo.class, null)
                .stream()
                .filter(tenant -> TenantStatus.ACTIVE.equals(tenant.getStatus()))
                .toList();
    }
}
