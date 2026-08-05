package io.softa.starter.tenant.provisioning;

import java.util.List;
import java.util.Map;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static io.softa.framework.base.context.ContextUtils.inSystemContext;
import io.softa.framework.base.utils.Assert;
import io.softa.starter.tenant.enums.TenantStatus;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSeedProgress;
import io.softa.starter.tenant.service.impl.TenantInfoServiceImpl;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

/**
 * Restarts a tenant's setup: clears the provisioning state this module owns, then announces provisioning again
 * so every seeder runs from the beginning.
 *
 * <h3>It does not delete business data, on purpose</h3>
 * The obvious implementation — sweep the tenant's rows out of every module's tables from here — works in one
 * process and breaks silently the moment a module becomes its own service. The sweep still compiles, still
 * runs, still reports success, and simply misses everything belonging to the service that moved out. Nothing
 * fails; the rebuild just quietly stops being a rebuild for that module.
 *
 * <p>So each seeder discards its own output instead, at the top of the same message handling that re-seeds it
 * (see {@link TenantSeedCleaner}). Provisioning is already distributed that way — every seeder subscribes to
 * the broadcast and writes its own data — and discarding follows the identical path, which means it keeps
 * working wherever those seeders are deployed.
 *
 * <p>It also removes the need for coordination: a service's clear and re-seed are serialized by being one
 * message, so there is no window where one service re-seeds while another is still deleting, and no
 * "everybody finished purging" latch to build and get wrong.
 *
 * <h3>What this method itself clears</h3>
 * Only what belongs to tenant-starter and to the tenant's own provisioning:
 * <ul>
 *   <li>the {@link TenantSeedProgress} ledger — not tidiness. {@code markSeederReady} flips a tenant to READY
 *       as soon as the DONE keys cover the expected set, so leaving the previous run's rows means the FIRST
 *       seeder to report satisfies that check alone, and the tenant is announced READY while the others are
 *       still seeding — opening login onto a half-built workspace;</li>
 *   <li>the tenant's accounts. They come from {@code createAdmin}, and a rebuild re-seeds the roles with new
 *       ids, so an account kept across it would hold grants pointing at roles that no longer exist — an admin
 *       who appears to have access and does not.</li>
 * </ul>
 *
 * <h3>Why deleting anything is safe here</h3>
 * Nobody can get into a tenant before it is READY — login and admin creation both refuse one — so a
 * not-yet-READY tenant holds only what provisioning put there. {@link #rebuild} enforces that itself rather
 * than trusting its caller, because the invariant is the whole basis for deleting at all.
 *
 * <p><b>Draft only, not "anything that is not READY".</b> {@code INITIALIZING} fails the check too, and that
 * is the point: it means seeders are running, and re-announcing provisioning next to them runs two rounds at
 * once. Each seeder discards and re-creates its own rows on a rebuild, so the two rounds do not merge — the
 * org masters end up carrying one round's ids while a chain that already read the other round's still points
 * at rows that have since been deleted. That is not a hypothetical: it is how a tenant ended up with an
 * {@code Employee} referencing a {@code LegalEntity} and a {@code Department} that no longer existed, visible
 * only once per-company narrowing shipped and its lists came back empty.
 *
 * <p>The way out of {@code INITIALIZING} is therefore not this method but
 * {@code TenantProvisioningStatusService.failTimedOut()}, which flips a tenant that has stopped making
 * progress to {@code DRAFT}. <b>That guard is load-bearing now</b>: with this check in place a stalled tenant
 * has no other exit, so a cron that never fires — or a clock skew that makes "last progress" look like the
 * future and every tenant look busy — leaves it stuck for good.
 *
 * <p>{@code TenantInfo} and its {@code TenantSubscription} survive: the tenant keeps its id, code and the
 * periods ops recorded. Neither is produced by provisioning, so re-running it would not bring them back.
 */
@Slf4j
@Service
public class TenantSeedPurgeService {

    /**
     * Provisioning state owned here, child-first.
     *
     * <p>The accounts are user-starter's models rather than a seeder's output, which is why they are named
     * explicitly instead of arriving through {@link TenantSeedCleaner}. In practice they are usually empty —
     * {@code createAdmin} refuses a tenant that is not READY, and a rebuild is only possible while it is not —
     * so they are listed so the outcome does not depend on that gate holding.
     */
    private static final List<String> PROVISIONING_STATE = List.of(
            "UserRoleRel", "UserInvitation", "UserProfile", "UserAccount", "TenantSeedProgress");

    private final TenantInfoServiceImpl tenantInfoService;
    private final TenantProvisioningStatusService statusService;
    private final TenantSeedCleaner seedCleaner;
    private final ApplicationEventPublisher eventPublisher;

    public TenantSeedPurgeService(TenantInfoServiceImpl tenantInfoService,
                                  TenantProvisioningStatusService statusService,
                                  TenantSeedCleaner seedCleaner,
                                  ApplicationEventPublisher eventPublisher) {
        this.tenantInfoService = tenantInfoService;
        this.statusService = statusService;
        this.seedCleaner = seedCleaner;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Clear the provisioning state and announce provisioning again.
     *
     * <p>Order matters twice. The state is cleared before the axis is reopened, so a stale DONE row cannot
     * satisfy the readiness check; and the axis is reopened before the announcement, so a seeder that finishes
     * fast cannot report completion before anything is waiting for it — the tenant would then sit in
     * INITIALIZING forever.
     *
     * <p>The announcement is the identical event {@code provision()} publishes, so the seeders run again
     * unchanged and this path grows no sequence of its own to drift from the real one.
     *
     * @param tenantId tenant to rebuild
     * @return provisioning-state rows removed here, per model. Business data cleared by the seeders themselves
     *         is not counted — that happens asynchronously, possibly in other services, after this returns
     */
    @Transactional(rollbackFor = Exception.class)
    public Map<String, Integer> rebuild(Long tenantId) {
        Assert.notNull(tenantId, "Tenant id is required to rebuild its setup.");
        TenantInfo tenant = tenantInfoService.getById(tenantId)
                .orElseThrow(() -> new IllegalArgumentException("Tenant " + tenantId + " not found."));
        Assert.isTrue(TenantStatus.DRAFT.equals(tenant.getStatus()),
                "Tenant {0} is {1}, and a rebuild starts from Draft only. A tenant that finished setup holds "
                        + "more than setup output, so discarding it is not on offer; one still Initializing has "
                        + "seeders running, and a stalled one reaches Draft through the provisioning-timeout "
                        + "guard, which is the way out.", tenantId, tenant.getStatus());

        Map<String, Integer> cleared = inSystemContext(
                () -> seedCleaner.clearModels(tenantId, PROVISIONING_STATE));
        statusService.beginProvisioning(tenantId);
        eventPublisher.publishEvent(new TenantProvisionedEvent(tenantId, tenant.getCode(), tenant.getName(), true));
        log.warn("Tenant {} setup restarted — provisioning state cleared {}; each seeder discards its own "
                + "data as it re-runs", tenantId, cleared);
        return cleared;
    }
}
