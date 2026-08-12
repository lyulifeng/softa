package io.softa.framework.base.context;

import java.util.function.Supplier;

/**
 * Helpers to run privileged work under a synthetic {@link Context}. The caller's own context is
 * either the wrong tenant or lacks the skip-permission flag, so these run the action under a fresh,
 * elevated context and restore the previous one on exit (via {@link ContextHolder#callWith}). Both
 * switches set {@code skipPermissionCheck} — they are for trusted, system-level orchestration
 * (provisioning, seeding), not for request-scoped business logic.
 *
 * <ul>
 *   <li>{@link #inSystemContext} — cross-tenant + permission-skipped: for shared / registry writes
 *       that are not scoped to a single tenant.</li>
 *   <li>{@link #inTenantContext} — pinned to one tenant, {@code crossTenant=false},
 *       permission-skipped: for privileged work that must stay inside one tenant's isolation.</li>
 * </ul>
 */
public final class ContextUtils {

    private ContextUtils() {
    }

    /**
     * Run under a system context: cross-tenant + permission-skipped (shared / registry writes).
     *
     * <p>Clones the caller's context rather than starting blank, so the INITIATOR'S identity
     * (userId / name / traceId) survives into everything the action writes — the change log of a
     * tenant provisioned by an ops user records that user, not "Unknown". {@code Context.copy()}
     * deliberately does not carry {@code skipPermissionCheck}, so the elevation below is still
     * explicit. Tenant / company pins are cleared: system scope must not inherit the caller's
     * tenant or header company selection. On a contextless thread (MQ consumer, cron)
     * {@code cloneContext()} returns a blank context — identical to the old behavior.
     */
    public static <T> T inSystemContext(Supplier<T> action) {
        Context ctx = ContextHolder.cloneContext();
        ctx.setTenantId(null);
        ctx.setCompanyId(null);
        // Identity for audit is userId/name; the tenant-bound profile objects don't travel.
        ctx.setUserInfo(null);
        ctx.setEmpInfo(null);
        ctx.setCrossTenant(true);
        ctx.setSkipPermissionCheck(true);
        return ContextHolder.callWith(ctx, action::get);
    }

    /** Void overload of {@link #inSystemContext(Supplier)}. */
    public static void inSystemContext(Runnable action) {
        inSystemContext(() -> {
            action.run();
            return null;
        });
    }

    /**
     * Run under a specific tenant's context: tenant-pinned + permission-skipped. {@code crossTenant}
     * is set to false <b>explicitly</b> — anything downstream that clones the current context (e.g. a
     * pre-data seeder) must stay tenant-isolated; inheriting {@code crossTenant=true} from a
     * surrounding system context would dissolve isolation.
     */
    public static <T> T inTenantContext(Long tenantId, Supplier<T> action) {
        // Clone-then-pin (see inSystemContext): keeps the initiator's identity for audit while the
        // tenant pin below overrides whatever tenant the caller was in. The caller's header company
        // selection is meaningless inside the target tenant, so it is cleared.
        Context ctx = ContextHolder.cloneContext();
        ctx.setTenantId(tenantId);
        ctx.setCompanyId(null);
        ctx.setUserInfo(null);
        ctx.setEmpInfo(null);
        ctx.setCrossTenant(false);
        ctx.setSkipPermissionCheck(true);
        return ContextHolder.callWith(ctx, action::get);
    }

    /** Void overload of {@link #inTenantContext(Long, Supplier)}. */
    public static void inTenantContext(Long tenantId, Runnable action) {
        inTenantContext(tenantId, () -> {
            action.run();
            return null;
        });
    }
}
