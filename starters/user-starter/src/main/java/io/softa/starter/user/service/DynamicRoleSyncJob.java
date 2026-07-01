package io.softa.starter.user.service;

/**
 * Re-evaluates role.dynamic_filter for every active role and synchronizes
 * the user_role table (source=DYNAMIC rows only). Runs on a Spring cron
 * schedule (default: every night at 02:00).
 *
 * Algorithm (per role with non-null dynamic_filter):
 *   1. Run the filter as ORM query → set of currently-matching user IDs.
 *   2. Diff against existing user_role rows where role_id=R AND source=DYNAMIC.
 *   3. INSERT new matches, DELETE rows no longer matching.
 *   4. Manual rows (source=MANUAL) are never touched.
 *   5. Affected user IDs are batch-evicted from PermissionInfo cache.
 *
 * INNER JOIN employee in the underlying SQL means pure users (no employee
 * record) are NEVER matched by dynamic rules — they can only be assigned
 * roles MANUALLY.
 */
public interface DynamicRoleSyncJob {

    /**
     * Sync a specific role's dynamic membership immediately (admin-triggered).
     * @return count of inserts + deletes performed
     */
    int syncRole(Long tenantId, Long roleId);

    /**
     * Sync all roles in all tenants. Invoked by the Spring scheduler.
     */
    void syncAll();

    /**
     * Re-evaluate every dynamic role's membership for a single user.
     * Intended for domain-event bridges that see an HR change (transfer /
     * hire / status flip) and need the affected user's dynamic-role
     * assignments to refresh without waiting for the next cron tick.
     *
     * <p>Runs the same delta logic as {@link #syncRole} but scoped to a
     * single (tenant, user) pair: loads every dynamic role in the tenant,
     * checks whether this user satisfies each rule now vs. the current
     * {@code user_role_rel.DYNAMIC} rows, inserts / deletes accordingly.
     * MANUAL rows are never touched.
     *
     * <p>Framework has no idea what "HR change" means — callers are
     * expected to be domain-specific bridges (e.g. {@code HrEventBridge}
     * in an HR business module) that translate their own domain events
     * into this call.
     *
     * @return count of inserts + deletes performed
     */
    int syncMembershipForUser(Long tenantId, Long userId);
}
