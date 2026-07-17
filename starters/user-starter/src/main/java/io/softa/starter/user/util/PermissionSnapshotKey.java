package io.softa.starter.user.util;

/**
 * Redis key for a user's cached permission snapshot.
 *
 * <p>Mirrors the permission engine's {@code PermissionSnapshotProvider.userSnapshotKey}
 * (the single source of truth). Duplicated here as a stable convention so
 * user-starter can READ the snapshot (/me, effective-perms) and INVALIDATE it
 * (on role / grant / employee change) at the exact same key the engine writes —
 * without any compile dependency on permission-starter.
 *
 * <p><b>KEEP IN SYNC</b> with {@code PermissionSnapshotProvider.CACHE_KEY_PREFIX} /
 * {@code userSnapshotKey}: a drift here means invalidation misses and a user
 * keeps stale permissions until TTL expiry.
 */
public final class PermissionSnapshotKey {

    private static final String PREFIX = "perm:";

    private PermissionSnapshotKey() {}

    /** {@code perm:{tenantId}:user:{userId}} */
    public static String forUser(Long tenantId, Long userId) {
        return PREFIX + tenantId + ":user:" + userId;
    }
}
