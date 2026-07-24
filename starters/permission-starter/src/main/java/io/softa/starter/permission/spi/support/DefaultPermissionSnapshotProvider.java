package io.softa.starter.permission.spi.support;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.web.context.request.RequestAttributes;
import org.springframework.web.context.request.RequestContextHolder;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.sensitive.SensitiveFieldSetCache;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.spi.ScopeType;

/**
 * Default {@link PermissionSnapshotProvider} — builds a user's snapshot from the
 * standard RBAC config models, reading them <b>by name</b> ({@link ModelService}
 * 约定读) into private view DTOs so this engine never imports {@code user-starter}.
 * Relocated here from {@code user-starter}'s {@code PermissionInfoEnricher}
 * (2026-07-16, F1): the enricher was authoring-adjacent but is actually driven by
 * the enforce path (lazily built on the first authenticated request via the
 * interceptor's {@code snapshotProvider.get()}), so it belongs beside the engine.
 *
 * <h3>{@code @SkipPermissionCheck} on {@link #get}</h3>
 * The RBAC reads below must NOT be scope-filtered — otherwise the current user's
 * (empty) scope on {@code Role}/{@code RoleNavigation} would fail-closed to zero
 * rows, and {@code ScopeFilterAspect} → {@code PermissionServiceImpl} →
 * {@code snapshotProvider.get()} would recurse. The interceptor calls {@code get}
 * through the bean proxy, so the {@code @SkipPermissionCheck} {@code @Around}
 * fires and sets the context skip flag for the whole build (all nested
 * ModelService reads honor it).
 *
 * <h3>Standalone deployments</h3>
 * When the RBAC models are absent (pure-enforce microservice without
 * {@code user-starter}), {@link #loadFromDb} returns {@code null} → callers
 * fail-closed. Such a deployment supplies its own {@link PermissionSnapshotProvider}
 * (e.g. an {@link AbstractCacheAsideSnapshotProvider} subclass re-sourcing via RPC).
 *
 * <h3>Cache</h3>
 * Three tiers, same as the former enricher: request-scoped (a single request
 * calls {@code get} 4+ times — interceptor + row-scope/field-mask/write-guard
 * AOP), then Redis ({@code perm:{tenant}:user:{user}}, TTL 1h), then DB build.
 */
@Slf4j
public class DefaultPermissionSnapshotProvider implements PermissionSnapshotProvider {

    private static final int CACHE_TTL_SECONDS = RedisConstant.ONE_HOUR;
    private static final int ANCESTOR_DEPTH_CAP = 32;

    /** Role code that bypasses all enforcement — must match the seeded role. */
    private static final String SUPER_ADMIN_CODE = "SUPER_ADMIN";

    private static final String M_USER_ROLE_REL = "UserRoleRel";
    private static final String M_ROLE = "Role";
    private static final String M_ROLE_NAV = "RoleNavigation";
    private static final String M_ROLE_SCOPE = "RoleDataScope";
    private static final String M_ROLE_SFS = "RoleSensitiveFieldSet";
    private static final String M_NAV = "Navigation";

    private final CacheService cacheService;
    private final ModelService<?> modelService;
    private final SensitiveFieldSetCache sensitiveFieldSetCache;

    /** leafNavId → root→leaf ancestor chain; lazily built from the Navigation
     *  tree (seed data — changes only on redeploy). */
    private volatile Map<String, List<String>> ancestorChains;

    public DefaultPermissionSnapshotProvider(CacheService cacheService,
                                             ModelService<?> modelService,
                                             SensitiveFieldSetCache sensitiveFieldSetCache) {
        this.cacheService = cacheService;
        this.modelService = modelService;
        this.sensitiveFieldSetCache = sensitiveFieldSetCache;
    }

    @Override
    @SkipPermissionCheck
    public PermissionInfo get(Long tenantId, Long userId) {
        String key = PermissionSnapshotProvider.userSnapshotKey(tenantId, userId);

        // Tier 1: request-scoped — same request, same user → memory.
        RequestAttributes ra = RequestContextHolder.getRequestAttributes();
        if (ra != null) {
            Object stashed = ra.getAttribute(key, RequestAttributes.SCOPE_REQUEST);
            if (stashed instanceof PermissionInfo pi) {
                return pi;
            }
        }

        // Tier 2: Redis.
        try {
            PermissionInfo cached = cacheService.get(key, PermissionInfo.class);
            if (cached != null) {
                stashInRequest(ra, key, cached);
                return cached;
            }
        } catch (Throwable t) {
            log.warn("PermissionInfo cache read failed for key={}; falling through to DB", key, t);
        }

        // Tier 3: build from RBAC config.
        PermissionInfo fresh = loadFromDb(tenantId, userId);
        if (fresh != null) {
            try {
                cacheService.save(key, fresh, CACHE_TTL_SECONDS);
            } catch (Throwable t) {
                log.warn("PermissionInfo cache write failed for key={}; continuing", key, t);
            }
            stashInRequest(ra, key, fresh);
        }
        return fresh;
    }

    private static void stashInRequest(RequestAttributes ra, String key, PermissionInfo pi) {
        if (ra != null) {
            ra.setAttribute(key, pi, RequestAttributes.SCOPE_REQUEST);
        }
    }

    // ─────────────────────── DB build (约定读) ───────────────────────

    private PermissionInfo loadFromDb(Long tenantId, Long userId) {
        try {
            return doLoadFromDb(userId);
        } catch (Throwable t) {
            // The reads throw when the RBAC models are absent (a standalone enforce
            // deployment without user-starter) — fail-closed. Such a deployment
            // supplies its own provider (RPC re-sourcer / keep-warm reader).
            log.warn("PermissionInfo build failed for user {} (tenant {}); fail-closed", userId, tenantId, t);
            return null;
        }
    }

    private PermissionInfo doLoadFromDb(Long userId) {
        List<RoleView> activeRoles = loadActiveRolesFor(userId);
        Set<String> roleCodes = activeRoles.stream()
                .map(RoleView::getCode)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toSet());

        if (roleCodes.contains(SUPER_ADMIN_CODE)) {
            return emptyGrantsSnapshot(roleCodes);
        }
        if (activeRoles.isEmpty()) {
            return emptyGrantsSnapshot(roleCodes);
        }
        List<Long> roleIds = activeRoles.stream().map(RoleView::getId).filter(Objects::nonNull).toList();
        if (roleIds.isEmpty()) {
            return emptyGrantsSnapshot(roleCodes);
        }

        // 3a. Navigation + permission grants.
        Set<String> navigations = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        List<RoleNavigationView> navGrants = modelService.searchList(M_ROLE_NAV,
                new FlexQuery(List.of("navigationId", "permissionIds"), new Filters().in("roleId", roleIds)),
                RoleNavigationView.class);
        for (RoleNavigationView rn : navGrants) {
            if (rn.getNavigationId() == null) {
                continue;
            }
            navigations.add(rn.getNavigationId());
            List<String> pids = JsonUtils.toStringList(rn.getPermissionIds(), true);
            if (pids != null) {
                permissions.addAll(pids);
            }
        }

        // 3b. Row-scope grants, keyed by model.
        Map<String, List<ScopeRule>> modelScopeMap = new HashMap<>();
        List<RoleDataScopeView> scopeGrants = modelService.searchList(M_ROLE_SCOPE,
                new FlexQuery(List.of("model", "dataScopes"), new Filters().in("roleId", roleIds)),
                RoleDataScopeView.class);
        for (RoleDataScopeView rds : scopeGrants) {
            String model = rds.getModel();
            if (model == null || model.isBlank()) {
                continue;
            }
            List<ScopeRule> scopes = parseScopeRules(rds.getDataScopes());
            if (!scopes.isEmpty()) {
                modelScopeMap.computeIfAbsent(model, k -> new ArrayList<>()).addAll(scopes);
            }
        }

        // 3c. Sensitive-field-set grants, keyed by the SFS's canonical model.
        Map<String, Set<String>> modelSensitiveFieldSetsMap = new HashMap<>();
        List<RoleSfsView> sfsGrants = modelService.searchList(M_ROLE_SFS,
                new FlexQuery(List.of("sensitiveFieldSetId"), new Filters().in("roleId", roleIds)),
                RoleSfsView.class);
        for (RoleSfsView g : sfsGrants) {
            String sid = g.getSensitiveFieldSetId();
            if (sid == null) {
                continue;
            }
            String sfsModel = sensitiveFieldSetCache.modelOf(sid);
            if (sfsModel == null) {
                continue;
            }
            modelSensitiveFieldSetsMap.computeIfAbsent(sfsModel, k -> new HashSet<>()).add(sid);
        }

        Set<String> expandedNavigations = expandAncestors(navigations);

        PermissionInfo info = new PermissionInfo();
        info.setRoleCodes(roleCodes);
        info.setNavigations(expandedNavigations);
        info.setPermissions(permissions);
        info.setModelScopeMap(modelScopeMap);
        info.setModelSensitiveFieldSetsMap(modelSensitiveFieldSetsMap);
        return info;
    }

    /** Roles for a user, filtered to active=true (inactive roles revoke their
     *  grants without deleting rows). */
    private List<RoleView> loadActiveRolesFor(Long userId) {
        List<UserRoleRelView> rels = modelService.searchList(M_USER_ROLE_REL,
                new FlexQuery(List.of("roleId"), new Filters().eq("userId", userId)),
                UserRoleRelView.class);
        if (rels.isEmpty()) {
            return List.of();
        }
        Set<Long> roleIds = rels.stream()
                .map(UserRoleRelView::getRoleId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (roleIds.isEmpty()) {
            return List.of();
        }
        return modelService.searchList(M_ROLE,
                new FlexQuery(List.of("id", "code", "active"),
                        new Filters().in("id", roleIds).eq("active", true)),
                RoleView.class);
    }

    private Set<String> expandAncestors(Set<String> leafNavIds) {
        if (leafNavIds.isEmpty()) {
            return Collections.emptySet();
        }
        Map<String, List<String>> chains = ancestorChains();
        Set<String> out = new LinkedHashSet<>(leafNavIds);
        for (String leaf : leafNavIds) {
            List<String> chain = chains.get(leaf);
            if (chain == null || chain.isEmpty()) {
                continue;
            }
            for (String id : chain) {
                if (!id.equals(leaf)) {
                    out.add(id);
                }
            }
        }
        return out;
    }

    /** Lazy leaf → ancestor-chain index, built from the Navigation tree; cached
     *  once non-empty (Navigation is seed data). */
    private Map<String, List<String>> ancestorChains() {
        Map<String, List<String>> cached = ancestorChains;
        if (cached != null) {
            return cached;
        }
        Map<String, List<String>> built = buildAncestorChains();
        if (!built.isEmpty()) {
            ancestorChains = built;
        }
        return built;
    }

    private Map<String, List<String>> buildAncestorChains() {
        List<NavigationView> all = modelService.searchList(M_NAV,
                new FlexQuery(List.of("id", "parentId"), new Filters()), NavigationView.class);
        if (all.isEmpty()) {
            return Map.of();
        }
        Map<String, NavigationView> byId = new HashMap<>(all.size());
        for (NavigationView n : all) {
            if (n.getId() != null) {
                byId.put(n.getId(), n);
            }
        }
        Map<String, List<String>> built = new HashMap<>(byId.size());
        for (NavigationView n : byId.values()) {
            String leafId = n.getId();
            List<String> chain = new ArrayList<>();
            chain.add(leafId);
            String cursor = n.getParentId();
            int guard = 0;
            Set<String> visited = new HashSet<>();
            visited.add(leafId);
            while (cursor != null && guard++ < ANCESTOR_DEPTH_CAP && visited.add(cursor)) {
                chain.add(cursor);
                NavigationView parent = byId.get(cursor);
                cursor = parent == null ? null : parent.getParentId();
            }
            Collections.reverse(chain);
            built.put(leafId, List.copyOf(chain));
        }
        return Map.copyOf(built);
    }

    /** Parse a {@code dataScopes} JSON array → ScopeRule list. Tolerant: skip
     *  rows missing scopeType or with an unknown enum value. */
    private static List<ScopeRule> parseScopeRules(JsonNode node) {
        if (node == null || !node.isArray() || node.isEmpty()) {
            return List.of();
        }
        List<ScopeRule> out = new ArrayList<>(node.size());
        for (JsonNode el : node) {
            if (!el.isObject()) {
                continue;
            }
            JsonNode typeNode = el.get("scopeType");
            if (typeNode == null || !typeNode.isString()) {
                continue;
            }
            ScopeType type;
            try {
                type = ScopeType.valueOf(typeNode.asString());
            } catch (IllegalArgumentException ignored) {
                continue;
            }
            ScopeRule rule = new ScopeRule();
            rule.setScopeType(type);
            rule.setScopeExpr(el.get("scopeExpr"));
            out.add(rule);
        }
        return out;
    }

    private static PermissionInfo emptyGrantsSnapshot(Set<String> roleCodes) {
        PermissionInfo info = new PermissionInfo();
        info.setRoleCodes(roleCodes);
        info.setNavigations(Collections.emptySet());
        info.setPermissions(Collections.emptySet());
        info.setModelScopeMap(Collections.emptyMap());
        info.setModelSensitiveFieldSetsMap(Collections.emptyMap());
        return info;
    }

    // ─────────────────────── view DTOs (约定读 projections) ───────────────────────
    // Public + no-arg ctor (@Data): BeanTool.mapToObject instantiates via the
    // no-arg constructor and sets fields reflectively — records would NOT work.

    @Data public static class RoleView {
        private Long id;
        private String code;
        private Boolean active;
    }

    @Data public static class UserRoleRelView {
        private Long roleId;
    }

    @Data public static class RoleNavigationView {
        private String navigationId;
        private JsonNode permissionIds;
    }

    @Data public static class RoleDataScopeView {
        private String model;
        private JsonNode dataScopes;
    }

    @Data public static class RoleSfsView {
        private String sensitiveFieldSetId;
    }

    @Data public static class NavigationView {
        private String id;
        private String parentId;
    }
}
