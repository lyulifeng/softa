package io.softa.starter.user.service.impl;

import java.util.ArrayList;
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

import org.springframework.stereotype.Service;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.node.ArrayNode;
import tools.jackson.databind.node.JsonNodeFactory;
import tools.jackson.databind.node.ObjectNode;

import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.user.constant.RoleConstant;
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
 * A JSON object mirroring the fields of the engine's {@code PermissionInfo} that the
 * FE reads — {@code roleCodes} / {@code superAdmin} / {@code navigations} /
 * {@code permissions} / {@code modelSensitiveFieldSetsMap} — so a cache-hit
 * (engine's serialized {@code PermissionInfo}) and this cache-miss fallback look the
 * same to the FE. Scope rules ({@code modelScopeMap}) are intentionally NOT built:
 * they drive server-side row filtering, the FE never reads them, and building them
 * would pull in the engine's {@code ScopeRule} type.
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

    private static final JsonNodeFactory JSON = JsonNodeFactory.instance;

    private final UserRoleRelService userRoleRelService;
    private final RoleService roleService;
    private final RoleNavigationService roleNavigationService;
    private final RoleSensitiveFieldSetService roleSensitiveFieldSetService;
    private final NavigationModelResolver navigationModelResolver;
    private final ModelService<?> modelService;

    /** leafNavId → root→leaf ancestor chain (inclusive). Built once from the same
     *  seed-only Navigation snapshot {@link NavigationModelResolver} exposes. */
    private volatile Map<String, List<String>> ancestorChains = Map.of();

    /**
     * Build the current user's UI context from user-starter's own RBAC entities.
     * Mirrors the engine's snapshot build (minus scope rules). Never returns null —
     * a user with no active roles yields an empty-grants object.
     */
    @SkipPermissionCheck
    public JsonNode build(Long userId) {
        List<Role> activeRoles = loadActiveRolesFor(userId);
        Set<String> roleCodes = activeRoles.stream()
                .map(Role::getCode)
                .filter(c -> c != null && !c.isEmpty())
                .collect(Collectors.toCollection(LinkedHashSet::new));

        ObjectNode out = JSON.objectNode();
        out.set("roleCodes", toArray(roleCodes));
        boolean superAdmin = roleCodes.contains(RoleConstant.CODE_SUPER_ADMIN);
        out.put("superAdmin", superAdmin);

        List<Long> roleIds = activeRoles.stream().map(Role::getId).filter(Objects::nonNull).toList();

        // SUPER_ADMIN (detected downstream by roleCodes) or no active roles →
        // empty-grants shape, matching the engine's emptyGrantsSnapshot.
        if (superAdmin || roleIds.isEmpty()) {
            return emptyGrants(out);
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

        out.set("navigations", toArray(expandAncestors(navigations)));
        out.set("permissions", toArray(permissions));
        out.set("modelSensitiveFieldSetsMap", buildModelSfsMap(roleIds));
        return out;
    }

    private static ObjectNode emptyGrants(ObjectNode out) {
        out.set("navigations", JSON.arrayNode());
        out.set("permissions", JSON.arrayNode());
        out.set("modelSensitiveFieldSetsMap", JSON.objectNode());
        return out;
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
    private ObjectNode buildModelSfsMap(List<Long> roleIds) {
        List<RoleSensitiveFieldSet> sfsGrants = roleSensitiveFieldSetService.searchList(new FlexQuery(
                List.of("sensitiveFieldSetId"),
                new Filters().in(RoleSensitiveFieldSet::getRoleId, roleIds)));
        Set<String> sfsIds = new HashSet<>();
        for (RoleSensitiveFieldSet g : sfsGrants) {
            if (g.getSensitiveFieldSetId() != null) sfsIds.add(g.getSensitiveFieldSetId());
        }
        ObjectNode obj = JSON.objectNode();
        if (sfsIds.isEmpty()) return obj;
        List<Map<String, Object>> defs = modelService.searchList("SensitiveFieldSet",
                new FlexQuery(List.of("id", "model"), new Filters().in("id", new ArrayList<>(sfsIds))));
        Map<String, Set<String>> byModel = new HashMap<>();
        for (Map<String, Object> m : defs) {
            Object id = m.get("id");
            Object model = m.get("model");
            if (id == null || model == null) continue;
            byModel.computeIfAbsent(model.toString(), k -> new HashSet<>()).add(id.toString());
        }
        byModel.forEach((model, ids) -> obj.set(model, toArray(ids)));
        return obj;
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

    private static ArrayNode toArray(Collection<String> vals) {
        ArrayNode arr = JSON.arrayNode();
        for (String v : vals) arr.add(v);
        return arr;
    }
}
