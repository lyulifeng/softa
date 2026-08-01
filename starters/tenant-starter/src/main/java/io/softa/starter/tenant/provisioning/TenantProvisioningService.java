package io.softa.starter.tenant.provisioning;

import java.time.LocalDateTime;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.Assert;

import static io.softa.framework.base.context.ContextUtils.inSystemContext;
import io.softa.starter.tenant.entity.TenantInfo;
import io.softa.starter.tenant.entity.TenantSubscription;
import io.softa.starter.tenant.enums.SubscriptionStatus;
import io.softa.starter.tenant.enums.TenantStatus;
import io.softa.starter.tenant.service.TenantSubscriptionPeriodService;
import io.softa.starter.tenant.service.TenantSubscriptionService;
import io.softa.starter.tenant.service.impl.TenantInfoServiceImpl;
import io.softa.starter.tenant.service.impl.TenantProvisioningStatusService;

/**
 * Tenant provisioning — a reusable tenant-starter feature. Creates the tenant registry row + its owned
 * 1:1 {@link TenantSubscription} (the version: planId + lifecycle + effective dates) in a <b>system
 * context</b> (crossTenant + skip-permission; both rows are shared / non-tenant-scoped), then publishes
 * {@link TenantProvisionedEvent} so the app can react.
 *
 * <p><b>Why an event, not direct calls:</b> per-tenant seeding lives in metadata-starter and the first
 * admin lives in user-starter — both are ⊥ to tenant-starter, so tenant-starter must not call them.
 * The app (which depends on all of them) listens for the event and does the seeding / admin creation.
 * The event fires <b>synchronously inside this transaction</b>, so those app-side reactions stay atomic
 * with tenant creation — a listener that throws rolls the whole provisioning back.
 */
@Slf4j
@Service
public class TenantProvisioningService {

    private final TenantInfoServiceImpl tenantInfoService;
    private final TenantSubscriptionService subscriptionService;
    private final TenantSubscriptionPeriodService periodService;
    private final ApplicationEventPublisher eventPublisher;
    private final TenantProvisioningStatusService provisioningStatusService;

    public TenantProvisioningService(TenantInfoServiceImpl tenantInfoService,
                                     TenantSubscriptionService subscriptionService,
                                     TenantSubscriptionPeriodService periodService,
                                     ApplicationEventPublisher eventPublisher,
                                     TenantProvisioningStatusService provisioningStatusService) {
        this.tenantInfoService = tenantInfoService;
        this.subscriptionService = subscriptionService;
        this.periodService = periodService;
        this.eventPublisher = eventPublisher;
        this.provisioningStatusService = provisioningStatusService;
    }

    @Transactional(rollbackFor = Exception.class)
    public ProvisionResult provision(ProvisionTenantRequest request) {
        Assert.notNull(request, "request must not be null");
        Assert.hasText(request.getName(), "name must not be blank");
        String code = normalizeCode(request.getCode(), request.getName());

        // System context — persist the subscription row + the registry row that links it.
        Long tenantId = inSystemContext(() -> {
            // Every tenant gets a subscription row at birth: it is the projection carrier the tenant list
            // and authorization read. It starts empty (NEVER_SUBSCRIBED, no plan) because a tenant is on
            // the floor plan by birth — "Free is not recorded" constrains the period table, not this row.
            TenantSubscription subscription = new TenantSubscription();
            subscription.setSubscriptionStatus(SubscriptionStatus.NEVER_SUBSCRIBED);
            Long subscriptionId = subscriptionService.createOne(subscription);

            TenantInfo tenant = new TenantInfo();
            tenant.setName(request.getName().trim());
            tenant.setCode(code);
            tenant.setStatus(TenantStatus.ACTIVE);
            tenant.setActivatedTime(LocalDateTime.now());
            tenant.setDefaultLanguage(request.getDefaultLanguage());
            tenant.setDefaultTimezone(request.getDefaultTimezone());
            tenant.setDefaultCurrency(request.getDefaultCurrency());
            tenant.setDefaultCountry(request.getDefaultCountry());
            tenant.setDataRegion(request.getDataRegion());
            tenant.setSubscriptionId(subscriptionId);
            Long newTenantId = tenantInfoService.createOne(tenant);

            // Back-link, and it has to be a second write: the subscription is created first so the tenant
            // can carry its id (the framework's 1:1 puts the FK on the owner), so the tenant id does not
            // exist yet at that point. See TenantSubscription.tenantId for why the back-link is worth it.
            // `updateOne(entity, true)` — nulls ignored — so this touches only tenantId. The full-overwrite
            // variant would carry the entity's virtual `periods` relation as null, which the framework reads
            // as "clear the relation".
            TenantSubscription backLink = new TenantSubscription();
            backLink.setId(subscriptionId);
            backLink.setTenantId(newTenantId);
            subscriptionService.updateOne(backLink, true);
            return newTenantId;
        });

        // Initial periods are optional: a tenant sold nothing yet simply has none and runs on the floor
        // plan. Recorded after the tenant row exists so the projection refresh can read its timezone.
        recordInitialPeriods(tenantId, request.getSubscriptionId());

        // Mark the initialization axis before broadcasting: no expected seeders (single-tenant / no-MQ)
        // → READY immediately; otherwise INITIALIZING until the expected seeders report done.
        provisioningStatusService.beginProvisioning(tenantId);

        // Synchronous, same-transaction: app-side listeners (seed per-tenant data, create first admin)
        // run before commit, so any failure there rolls back tenant creation too.
        eventPublisher.publishEvent(new TenantProvisionedEvent(tenantId, code, request.getName().trim(), false));

        log.info("Provisioned tenant id={} code={}", tenantId, code);
        return new ProvisionResult(tenantId, code);
    }

    /**
     * Record the subscription periods the create request carried, so a customer buying Pro on day one is
     * done in one submit rather than created on the floor plan and upgraded afterwards.
     *
     * <p>Optional by design: a tenant nobody has sold anything to yet simply has no period and runs on the
     * floor plan.
     *
     * <p>Handed to the period service's patch entry point — the same one the detail form's edits go through,
     * so create and edit share one write path with the same guards and the same projection refresh. What
     * must not happen is letting the framework's nested-relation pipeline persist this: it writes via the
     * generic {@code ModelService}, which runs no guards and leaves the tenant list reading NEVER_SUBSCRIBED.
     */
    private void recordInitialPeriods(Long tenantId, ProvisionTenantRequest.SubscriptionInput input) {
        if (input == null || input.getPeriods() == null) {
            return;
        }
        TenantInfo tenant = tenantInfoService.getById(tenantId).orElse(null);
        if (tenant == null || tenant.getSubscriptionId() == null) {
            return;
        }
        inSystemContext(() -> {
            periodService.applyPatch(tenant.getSubscriptionId(), input.getPeriods());
            return null;
        });
    }

    /** Use the supplied code when present, else slug the name; lower-kebab, ≤64 chars. */
    private String normalizeCode(String code, String name) {
        String raw = (code != null && !code.isBlank()) ? code : name;
        String slug = raw.trim().toLowerCase()
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-+)|(-+$)", "");
        if (slug.length() > 64) {
            slug = slug.substring(0, 64).replaceAll("-+$", "");
        }
        return slug.isBlank() ? "tenant" : slug;
    }
}
