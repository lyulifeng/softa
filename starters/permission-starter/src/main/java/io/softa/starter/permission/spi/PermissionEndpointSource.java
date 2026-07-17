package io.softa.starter.permission.spi;

import java.util.List;

/**
 * SPI (2026-07-15「端点数据倒置」接缝): supplies the raw endpoint→permission
 * rows that {@code EndpointIndex} (permission-starter) turns into its runtime
 * URL→permission reverse index.
 *
 * <p>Inverts the last hard dependency the endpoint gate had on the RBAC data
 * model: the interceptor's {@code EndpointIndex} used to read the
 * {@code Permission} entity (and resolve navigation→model) directly, which
 * chained {@code permission-starter} onto {@code user-starter}. Now the enforce
 * side depends only on this contract; {@code user-starter} implements it by
 * enumerating its {@code Permission} rows.
 *
 * <p>All the endpoint-derivation logic (standard CRUD action map, L1/L2 lookup
 * propagation, pattern compilation) stays in {@code EndpointIndex} — the impl
 * only hands over per-permission raw facts.
 *
 * <p>System-wide, tenant-independent, seed-only data — read once at startup.
 */
public interface PermissionEndpointSource {

    /**
     * @return one entry per permission row; order irrelevant. Empty list is
     *         valid (no permissions configured → every gated endpoint 403s).
     */
    List<PermissionEndpointDef> getPermissionEndpoints();

    /**
     * Raw per-permission endpoint facts, decoupled from the {@code Permission}
     * entity.
     *
     * @param permissionId      the permission id (its last {@code .}-segment is
     *                          the standard action used for derivation)
     * @param explicitEndpoints verbatim {@code "VERB /path"} entries when the
     *                          permission declares them; {@code null} / empty
     *                          means "derive from {@link #model} + action"
     * @param model             the permission's model (PascalCase) used for
     *                          standard-CRUD + lookup derivation; may be
     *                          {@code null} when the permission has neither
     *                          explicit endpoints nor a resolvable model
     *                          (such a permission registers nothing)
     */
    record PermissionEndpointDef(String permissionId, List<String> explicitEndpoints, String model) {}
}
