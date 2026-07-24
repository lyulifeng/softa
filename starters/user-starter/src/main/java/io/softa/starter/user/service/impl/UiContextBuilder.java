package io.softa.starter.user.service.impl;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.user.constant.RoleConstant;
import io.softa.starter.user.dto.EffectiveAccess;
import io.softa.starter.user.dto.UiContext;
import io.softa.starter.user.entity.Navigation;
import io.softa.starter.user.entity.Role;
import io.softa.starter.user.entity.RoleNavigation;
import io.softa.starter.user.entity.RoleSensitiveFieldSet;
import io.softa.starter.user.entity.UserRoleRel;
import io.softa.starter.user.service.NavigationModelResolver;
import io.softa.starter.user.service.RoleNavigationService;
import io.softa.starter.user.service.RoleSensitiveFieldSetService;
import io.softa.starter.user.service.RoleService;
import io.softa.starter.user.service.UserRoleRelService;
import io.softa.starter.user.util.JsonArrayUtils;
import io.softa.starter.user.util.NavIds;

/**
 * Fallback builder for {@code /me/uiContext} when the permission engine's cached
 * snapshot is cold (Redis miss / not yet warmed / Redis blip).
 *
 * <h3>Why user-starter can build this without the engine</h3>
 * The RBAC config models ({@code Role} / {@code UserRoleRel} / {@code RoleNavigation}
 * / {@code RoleSensitiveFieldSet} / {@code Navigation} / {@code SensitiveFieldSet})
 * are user-starter's OWN entities, so it reads them directly — no dependency on
 * {@code permission-starter}. The engine's {@code DefaultPermissionSnapshotProvider}
 * builds the same shape via 约定读 view DTOs precisely because it's ⊥ of user-starter
 * and can't see these entities; user-starter can.
 *
 * <h3>Output shape</h3>
 * Returns a typed {@link UiContext} — the FE-facing subset of the engine's {@code PermissionInfo}
 * ({@code roleCodes} / {@code navigations} / {@code permissions} / {@code modelSensitiveFieldSetsMap}),
 * so a cache-hit (the engine's serialized {@code PermissionInfo}, deserialized into the same
 * {@code UiContext}) and this cache-miss fallback are identical to the FE. NOT built: {@code superAdmin}
 * (the FE derives it from {@code roleCodes}), {@code permissionCodes} (server-side), and scope rules
 * ({@code modelScopeMap}) — the latter drive server-side row filtering, the FE never reads them, and
 * building them would pull in the engine's {@code ScopeRule} type (permission-starter, ⊥ user-starter).
 *
 * <h3>Consistency caveat</h3>
 * This is a SECOND assembly of "the user's effective nav / permissions" alongside the
 * engine's (which enforces). They derive from the same entities with the same rules
 * (active-role filter, SUPER_ADMIN short-circuit, ancestor expansion), but a future
 * change must touch both or the FE view drifts from enforcement — a UX/consistency
 * risk, never a security one (the interceptor stays authoritative).
 *
 * <h3>{@code @SkipPermissionCheck}</h3>
 * {@link #build} reads RBAC config models that a normal user can't see under scope
 * filtering; the annotation (honored on the proxied cross-bean call from
 * {@code MeController}) bypasses the scope aspect for these reads — same reason the
 * former {@code PermissionInfoEnricher.enrich} carried it.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UiContextBuilder {

    private final UserRoleRelService userRoleRelService;
    private final RoleService roleService;
    private final RoleNavigationService roleNavigationService;
    private final RoleSensitiveFieldSetService roleSensitiveFieldSetService;
    private final NavigationModelResolver navigationModelResolver;
    private final ModelService<?> modelService;

    /** Platform-only nav-id prefixes (never in a tenant admin's grant); from
     *  {@code permission.platform-nav-prefixes}. Field-injected — the ctor is RequiredArgs over finals. */
    @Value("${permission.platform-nav-prefixes:}")
    private String platformNavPrefixesCsv;

    /** Plan (entitlement) gate — optional: a pure-enforce deployment without tenant-starter has none,
     *  in which case every module is treated as entitled (no plan narrowing). */
    @org.springframework.beans.factory.annotation.Autowired(required = false)
    private io.softa.framework.orm.service.EntitlementService entitlementService;

    /** leafNavId → root→leaf ancestor chain (inclusive). Built once from the same
     *  seed-only Navigation snapshot {@link NavigationModelResolver} exposes. */
    private volatile Map<String, List<String>> ancestorChains = Map.of();

    /**
     * Build the current user's UI context from user-starter's own RBAC entities.
     * Mirrors the engine's snapshot build (minus scope rules). Never returns null —
     * a user with no active roles yields an empty-grants object.
     */
    @SkipPermissionCheck
    public UiContext build(Long userId) {
        List<Role> activeRoles = loadActiveRolesFor(userId);
        Set<String> roleCodes = activeRoles.stream()
                .map(Role::getCode)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        UiContext out = new UiContext();
        out.setRoleCodes(roleCodes);
        boolean superAdmin = roleCodes.contains(RoleConstant.CODE_SUPER_ADMIN);

        List<Long> roleIds = activeRoles.stream().map(Role::getId).filter(Objects::nonNull).toList();

        // SUPER_ADMIN (detected downstream by roleCodes) or no active roles →
        // empty-grants shape, matching the engine's emptyGrantsSnapshot.
        if (superAdmin || roleIds.isEmpty()) {
            return emptyGrants(out);
        }

        // TENANT_ADMIN → all tenant-facing navs (all minus platform-only prefixes) + their
        // permissions, computed at runtime (mirrors DefaultPermissionSnapshotProvider). The tenant's
        // plan narrows this further on the FE via entitledModules.
        if (roleCodes.contains(RoleConstant.CODE_TENANT_ADMIN)) {
            return tenantAdminGrants(out);
        }

        // Navigation + permission grants.
        Set<String> navigations = new HashSet<>();
        Set<String> permissions = new HashSet<>();
        List<RoleNavigation> navGrants = roleNavigationService.searchList(new FlexQuery(
                List.of("navigationId", "permissionIds"),
                new Filters().in(RoleNavigation::getRoleId, roleIds)));
        for (RoleNavigation rn : navGrants) {
            if (rn.getNavigationId() == null) continue;
            navigations.add(rn.getNavigationId());
            permissions.addAll(JsonArrayUtils.toStringList(rn.getPermissionIds(), true));
        }

        out.setNavigations(expandAncestors(navigations));
        out.setPermissions(permissions);
        out.setModelSensitiveFieldSetsMap(buildModelSfsMap(roleIds));
        return out;
    }

    private static UiContext emptyGrants(UiContext out) {
        out.setNavigations(Set.of());
        out.setPermissions(Set.of());
        out.setModelSensitiveFieldSetsMap(Map.of());
        return out;
    }

    /** Tenant super-admin: every tenant-facing nav (all minus platform-only prefixes) + those navs'
     *  permissions. Plan narrowing happens on the FE via entitledModules; SFS map empty (sees all). */
    private UiContext tenantAdminGrants(UiContext out) {
        Collection<Navigation> allNavs = navigationModelResolver.allNavigations();
        Set<String> navigations = new HashSet<>();
        if (allNavs != null) {
            for (Navigation n : allNavs) {
                if (n != null && n.getId() != null && !isPlatformNav(n.getId())) {
                    navigations.add(n.getId());
                }
            }
        }
        Set<String> permissions = new HashSet<>();
        List<Map<String, Object>> perms = modelService.searchList("Permission",
                new FlexQuery(List.of("id", "navigationId"), new Filters()));
        for (Map<String, Object> p : perms) {
            Object id = p.get("id");
            Object navId = p.get("navigationId");
            if (id != null && navId != null && navigations.contains(navId.toString())) {
                permissions.add(id.toString());
            }
        }
        out.setNavigations(navigations);
        out.setPermissions(permissions);
        out.setModelSensitiveFieldSetsMap(Map.of());
        return out;
    }

    /** True when a nav id falls under a configured platform-only prefix (never tenant-facing). */
    private boolean isPlatformNav(String navId) {
        if (platformNavPrefixesCsv == null || platformNavPrefixesCsv.isBlank()) {
            return false;
        }
        for (String prefix : platformNavPrefixesCsv.split(",")) {
            String p = prefix.trim();
            if (!p.isEmpty() && navId.startsWith(p)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Read-only <b>effective</b> access for an admin ROLE (role-detail display), mirroring the runtime
     * bypass computation. {@code SUPER_ADMIN} → every navigation + permission (platform included).
     * {@code TENANT_ADMIN} → all tenant-facing navs (minus platform-only prefixes), narrowed by the
     * tenant's plan ({@code entitledModules}), plus those navs' permissions. Row-scope and sensitive
     * fields are unrestricted (admin bypass), signalled by {@code dataScopeAll}/{@code sensitiveAll}.
     * Returns {@code computed = false} for a non-admin role — the caller then shows that role's static grants.
     */
    @SkipPermissionCheck
    public EffectiveAccess effectiveAccessForRole(String roleCode, Long tenantId) {
        EffectiveAccess out = new EffectiveAccess();
        boolean superAdmin = RoleConstant.CODE_SUPER_ADMIN.equals(roleCode);
        boolean tenantAdmin = RoleConstant.CODE_TENANT_ADMIN.equals(roleCode);
        if (!superAdmin && !tenantAdmin) {
            return out;   // computed = false — non-admin role; the caller renders its static grants.
        }
        Set<String> navigations = new HashSet<>();
        Collection<Navigation> allNavs = navigationModelResolver.allNavigations();
        if (allNavs != null) {
            for (Navigation n : allNavs) {
                if (n == null || n.getId() == null) continue;
                String navId = n.getId();
                // SUPER_ADMIN keeps everything; TENANT_ADMIN drops platform-only + plan-excluded navs.
                if (tenantAdmin && (isPlatformNav(navId) || !isEntitled(navId, tenantId))) {
                    continue;
                }
                navigations.add(navId);
            }
        }
        Set<String> permissions = new HashSet<>();
        List<Map<String, Object>> perms = modelService.searchList("Permission",
                new FlexQuery(List.of("id", "navigationId"), new Filters()));
        for (Map<String, Object> p : perms) {
            Object id = p.get("id");
            Object navId = p.get("navigationId");
            if (id != null && navId != null && navigations.contains(navId.toString())) {
                permissions.add(id.toString());
            }
        }
        out.setComputed(true);
        out.setRoleCode(roleCode);
        out.setNavigations(navigations);
        out.setPermissions(permissions);
        out.setDataScopeAll(true);   // admin bypasses row-scope
        out.setSensitiveAll(true);   // admin sees all sensitive fields
        return out;
    }

    /** Plan ({@code entitledModules}) narrowing — mirrors the FE {@code navModuleOf} gating. No gate
     *  installed (bean absent) or null tenant → everything entitled (no narrowing). */
    private boolean isEntitled(String navId, Long tenantId) {
        if (entitlementService == null || tenantId == null) {
            return true;
        }
        Set<String> entitled = entitlementService.entitledModules(tenantId);
        if (entitled == null) {
            return true;
        }
        String moduleId = NavIds.moduleOf(navId);
        return moduleId == null || entitled.contains(moduleId);
    }

    /** Roles for a user, filtered to active=true (inactive = revoked, per design §3.5). */
    private List<Role> loadActiveRolesFor(Long userId) {
        List<UserRoleRel> rels = userRoleRelService.searchList(new FlexQuery(
                List.of("roleId"), new Filters().eq(UserRoleRel::getUserId, userId)));
        if (rels.isEmpty()) return List.of();
        Set<Long> roleIds = rels.stream()
                .map(UserRoleRel::getRoleId).filter(Objects::nonNull).collect(Collectors.toSet());
        if (roleIds.isEmpty()) return List.of();
        return roleService.searchList(new FlexQuery(
                List.of("id", "code", "active"),
                new Filters().in(Role::getId, roleIds).eq(Role::getActive, true)));
    }

    /** Granted sensitive-field-set ids grouped by their canonical model. SFS id→model
     *  read via ModelService (no SensitiveFieldSetService in user-starter). */
    private Map<String, Set<String>> buildModelSfsMap(List<Long> roleIds) {
        List<RoleSensitiveFieldSet> sfsGrants = roleSensitiveFieldSetService.searchList(new FlexQuery(
                List.of("sensitiveFieldSetId"),
                new Filters().in(RoleSensitiveFieldSet::getRoleId, roleIds)));
        Set<String> sfsIds = new HashSet<>();
        for (RoleSensitiveFieldSet g : sfsGrants) {
            if (g.getSensitiveFieldSetId() != null) sfsIds.add(g.getSensitiveFieldSetId());
        }
        if (sfsIds.isEmpty()) return Map.of();
        List<Map<String, Object>> defs = modelService.searchList("SensitiveFieldSet",
                new FlexQuery(List.of("id", "model"), new Filters().in("id", new ArrayList<>(sfsIds))));
        Map<String, Set<String>> byModel = new HashMap<>();
        for (Map<String, Object> m : defs) {
            Object id = m.get("id");
            Object model = m.get("model");
            if (id == null || model == null) continue;
            byModel.computeIfAbsent(model.toString(), k -> new HashSet<>()).add(id.toString());
        }
        return byModel;
    }

    /** A granted child nav implies its container ancestors are visible. */
    private Set<String> expandAncestors(Set<String> leafNavIds) {
        if (leafNavIds.isEmpty()) return Collections.emptySet();
        Set<String> out = new LinkedHashSet<>(leafNavIds);
        Map<String, List<String>> chains = ancestorChains;
        for (String leaf : leafNavIds) {
            List<String> chain = chains.get(leaf);
            if (chain == null) continue;
            for (String id : chain) {
                if (!id.equals(leaf)) out.add(id);
            }
        }
        return out;
    }

    /** Build the leaf→ancestor-chain index once (nav tree is seed-only). Mirrors the
     *  former {@code PermissionInfoEnricher.initAncestorIndex}: root→leaf order,
     *  cycle guard + depth cap 32. */
    @PostConstruct
    void initAncestorIndex() {
        Collection<Navigation> all = navigationModelResolver.allNavigations();
        if (all == null || all.isEmpty()) {
            ancestorChains = Map.of();
            return;
        }
        Map<String, Navigation> byId = new HashMap<>(all.size());
        for (Navigation n : all) {
            if (n != null && n.getId() != null) byId.put(n.getId(), n);
        }
        Map<String, List<String>> built = new HashMap<>(byId.size());
        for (Navigation n : byId.values()) {
            String leafId = n.getId();
            List<String> chain = new ArrayList<>();
            chain.add(leafId);
            String cursor = n.getParentId();
            int guard = 0;
            Set<String> visited = new HashSet<>();
            visited.add(leafId);
            while (cursor != null && guard++ < 32 && visited.add(cursor)) {
                chain.add(cursor);
                Navigation parent = byId.get(cursor);
                cursor = parent == null ? null : parent.getParentId();
            }
            Collections.reverse(chain);
            built.put(leafId, List.copyOf(chain));
        }
        ancestorChains = Map.copyOf(built);
        log.debug("UiContextBuilder — ancestor chain index built for {} navigation(s)", built.size());
    }
}
