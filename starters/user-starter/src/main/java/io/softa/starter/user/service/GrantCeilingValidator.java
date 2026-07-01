package io.softa.starter.user.service;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.exception.BusinessException;
import io.softa.starter.user.dto.PermissionInfo;
import io.softa.starter.user.dto.ScopeRule;
import io.softa.starter.user.enums.ScopeType;

/**
 * Grant Ceiling — enforces that a role editor can only grant permissions,
 * SFS grants, scope types, and navigation access they themselves already
 * hold. Blocks the "role manager造 SUPER_ADMIN 之外的普通提权 role" attack
 * class (Known-Issues C3).
 *
 * <h3>Two consumers</h3>
 * <ol>
 *   <li>{@code NavigationConfigOptionsController} — UI options filtering:
 *       don't return SFS / scope options the editor can't grant, don't
 *       return the nav itself if editor lacks access. Also serves as an
 *       info-leak defence (Alice doesn't see the categories she has no
 *       right to know about).</li>
 *   <li>{@code RoleController.saveWizard} — server-side ceiling check:
 *       reject payloads that request permissions/SFS/scope/nav outside
 *       the editor's own grants. UI filtering is bypassable (crafted
 *       requests); this is the authority.</li>
 * </ol>
 *
 * <h3>Full-set check (not delta)</h3>
 * The wizard save path is wipe-and-rewrite (deletes all existing
 * role_navigation rows for the role, then inserts new ones from the
 * payload). So the payload is the caller's <em>complete intended
 * post-state</em>. Ceiling check on the full payload — anything in the
 * payload must be within editor's grants. Editor cannot "preserve" a
 * grant they don't have by including it in the payload; if the current
 * role has such a grant and editor edits the role, editor must either
 * uncheck those grants or ask a higher-privileged admin to make the
 * edit.
 *
 * <h3>Scope ceiling policy</h3>
 * Conservative: editor must hold the exact same scope type to grant it
 * (except {@code ALL}, which subsumes everything). {@code CUSTOM} is
 * special — only editors with {@code ALL} scope may grant it, because
 * a custom expression can theoretically express any range.
 *
 * <p>Not implemented (yet): a partial hierarchy where an editor with
 * {@code DEPT_SUBTREE} may grant {@code SELF} to subordinates. This is
 * a defensible extension but out of MVP scope.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class GrantCeilingValidator {

    private final PermissionInfoEnricher permissionInfoEnricher;
    private final NavigationModelResolver navResolver;

    /**
     * Snapshot of an editor's grants for use across a single wizard
     * request. Loading {@link PermissionInfo} once and threading it
     * through both option-filtering and save-time validation avoids
     * repeatedly calling {@code enrich} inside a nav loop.
     */
    public EditorGrants snapshot(Long tenantId, Long userId) {
        PermissionInfo pi = permissionInfoEnricher.enrich(tenantId, userId);
        return new EditorGrants(pi);
    }

    /**
     * Server-side check for a single {@code role_navigation} JSON row —
     * used by {@code RoleController.saveWizard}. Throws {@link BusinessException}
     * with a specific message on the first ceiling breach.
     *
     * @param editor caller's grants snapshot ({@link #snapshot})
     * @param row    one element of {@code WizardSaveDTO.roleNavigations}
     *               (JsonNode object with {@code navigationId} /
     *               {@code permissionIds} / {@code dataScopes} /
     *               {@code sensitiveFieldSetIds})
     */
    public void validateRoleNavigationRow(EditorGrants editor, JsonNode row) {
        String navId = textField(row, "navigationId");
        if (navId == null || navId.isEmpty()) {
            throw new BusinessException("role_navigation row missing navigationId");
        }

        // Known-Issues H12: CUSTOM scope must carry a non-empty scopeExpr.
        // Applies to super-admin too — a "CUSTOM without expression" row
        // silently degrades to EMPTY at compile time (fail-closed post-R5,
        // but still meaningless config that admins waste time debugging).
        // Runs before the super-admin bypass so H12 protection is universal.
        validateCustomScopeExprPresent(navId, row.get("dataScopes"));

        if (editor.isSuperAdmin()) return;   // 超管无 ceiling

        // 1. Navigation ceiling
        if (!editor.getNavigations().contains(navId)) {
            throw new BusinessException(
                    "Cannot grant navigation you don't hold: " + navId);
        }

        String primaryModel = navResolver.resolvePrimaryModel(navId);

        // 2. Permission ceiling
        Set<String> requestedPerms = stringArray(row.get("permissionIds"));
        for (String pid : requestedPerms) {
            if (!editor.getPermissions().contains(pid)) {
                throw new BusinessException(
                        "Cannot grant permission you don't hold: " + pid);
            }
        }

        // 3. SFS ceiling (per primary model)
        Set<String> requestedSfs = stringArray(row.get("sensitiveFieldSetIds"));
        if (!requestedSfs.isEmpty()) {
            Set<String> editorSfs = editor.sensitiveFieldSetsFor(primaryModel);
            for (String sfsId : requestedSfs) {
                if (!editorSfs.contains(sfsId)) {
                    throw new BusinessException(
                            "Cannot grant sensitive field set you don't hold on "
                                    + primaryModel + ": " + sfsId);
                }
            }
        }

        // 4. Scope ceiling (per primary model)
        List<ScopeType> requestedScopeTypes = extractScopeTypes(row.get("dataScopes"));
        for (ScopeType st : requestedScopeTypes) {
            if (!canGrantScope(editor, primaryModel, st)) {
                throw new BusinessException(
                        "Cannot grant scope type you don't hold on "
                                + primaryModel + ": " + st);
            }
        }
    }

    /**
     * UI option filtering: is {@code scopeType} grantable by {@code editor}
     * on {@code model}? Used by {@code NavigationConfigOptionsController}
     * to trim the wizard's scope-type picker.
     *
     * <p>Policy:
     * <ul>
     *   <li>Editor has {@code ALL} → can grant anything</li>
     *   <li>{@code CUSTOM} → only if editor has {@code ALL} (a custom
     *       expression can express any range, so require max privilege)</li>
     *   <li>Else → editor must hold the exact same scope type</li>
     * </ul>
     */
    public boolean canGrantScope(EditorGrants editor, String model, ScopeType scopeType) {
        if (editor.isSuperAdmin()) return true;
        Set<ScopeType> editorScopes = editor.scopeTypesFor(model);
        if (editorScopes.contains(ScopeType.ALL)) return true;
        if (scopeType == ScopeType.CUSTOM) return false;
        return editorScopes.contains(scopeType);
    }

    /** Is {@code sfsId} grantable by {@code editor} on {@code model}? */
    public boolean canGrantSensitiveFieldSet(EditorGrants editor, String model, String sfsId) {
        if (editor.isSuperAdmin()) return true;
        return editor.sensitiveFieldSetsFor(model).contains(sfsId);
    }

    /** Does {@code editor} have access to nav {@code navId}? */
    public boolean canGrantNavigation(EditorGrants editor, String navId) {
        if (editor.isSuperAdmin()) return true;
        return editor.getNavigations().contains(navId);
    }

    // ────────────────────────── JSON helpers ──────────────────────────

    private static String textField(JsonNode row, String field) {
        if (row == null) return null;
        JsonNode n = row.get(field);
        if (n == null || n.isNull()) return null;
        return n.asString();
    }

    private static Set<String> stringArray(JsonNode arr) {
        if (arr == null || !arr.isArray() || arr.isEmpty()) return Collections.emptySet();
        Set<String> out = new HashSet<>();
        for (JsonNode n : arr) {
            if (n == null || n.isNull()) continue;
            out.add(n.asString());
        }
        return out;
    }

    /**
     * Reject a {@code dataScopes} row that ticks CUSTOM without supplying a
     * concrete {@code scopeExpr}. Known-Issues H12 — client-side validation
     * (RoleWizardShell.handleSave) is the primary UX gate; this is the
     * server-side authority for scripted / crafted requests.
     *
     * <p>"Empty" means: missing key, JSON null, empty array, or empty object.
     * All four end up producing {@link Filters#EMPTY} in
     * {@code CustomScopeContributor.compile} and would post-R5 degrade the
     * whole rule to NEVER — silently granting nothing. Fail loudly at save
     * time so the admin can either fill the expression or uncheck CUSTOM.
     */
    private static void validateCustomScopeExprPresent(String navId, JsonNode dataScopes) {
        if (dataScopes == null || !dataScopes.isArray() || dataScopes.isEmpty()) return;
        for (JsonNode rule : dataScopes) {
            if (rule == null || !rule.isObject()) continue;
            JsonNode t = rule.get("scopeType");
            if (t == null || t.isNull()) continue;
            if (!ScopeType.CUSTOM.name().equals(t.asString())) continue;
            JsonNode expr = rule.get("scopeExpr");
            if (expr == null || expr.isNull()) {
                throw new BusinessException(
                        "CUSTOM scope on navigation " + navId + " requires a non-empty scopeExpr");
            }
            if (expr.isArray() && expr.isEmpty()) {
                throw new BusinessException(
                        "CUSTOM scope on navigation " + navId + " has an empty scopeExpr array");
            }
            if (expr.isObject() && expr.properties().isEmpty()) {
                throw new BusinessException(
                        "CUSTOM scope on navigation " + navId + " has an empty scopeExpr object");
            }
        }
    }

    /** Parse {@code dataScopes} JSON — array of {@code {scopeType, scopeExpr?}} — extract scope types. */
    private static List<ScopeType> extractScopeTypes(JsonNode dataScopes) {
        if (dataScopes == null || !dataScopes.isArray() || dataScopes.isEmpty()) {
            return Collections.emptyList();
        }
        List<ScopeType> out = new ArrayList<>();
        for (JsonNode rule : dataScopes) {
            if (rule == null || !rule.isObject()) continue;
            JsonNode t = rule.get("scopeType");
            if (t == null || t.isNull()) continue;
            try {
                out.add(ScopeType.valueOf(t.asString()));
            } catch (IllegalArgumentException ignored) {
                // Unknown scope type — treated as invalid ceiling breach
                // via BusinessException in the caller (row-level validate).
                // Ignoring here would silently drop; better to surface.
                throw new BusinessException(
                        "Unknown scopeType in dataScopes: " + t.asString());
            }
        }
        return out;
    }

    /**
     * Immutable projection of a caller's grants — read-only view over
     * {@link PermissionInfo} tailored to ceiling checks. Handles
     * null-safety of the internal maps once, so downstream callers can
     * just call {@code editor.getPermissions()} without null guards.
     */
    public static final class EditorGrants {
        private final PermissionInfo pi;

        EditorGrants(PermissionInfo pi) {
            this.pi = pi;
        }

        public boolean isSuperAdmin() {
            return pi != null && pi.isSuperAdmin();
        }

        public Set<String> getPermissions() {
            return pi == null || pi.getPermissions() == null
                    ? Collections.emptySet() : pi.getPermissions();
        }

        public Set<String> getNavigations() {
            return pi == null || pi.getNavigations() == null
                    ? Collections.emptySet() : pi.getNavigations();
        }

        public Set<String> sensitiveFieldSetsFor(String model) {
            if (pi == null || pi.getModelSensitiveFieldSetsMap() == null) return Collections.emptySet();
            Set<String> s = pi.getModelSensitiveFieldSetsMap().get(model);
            return s == null ? Collections.emptySet() : s;
        }

        public Set<ScopeType> scopeTypesFor(String model) {
            if (pi == null || pi.getModelScopeMap() == null) return Collections.emptySet();
            List<ScopeRule> rules = pi.getModelScopeMap().get(model);
            if (rules == null || rules.isEmpty()) return Collections.emptySet();
            Set<ScopeType> out = new HashSet<>();
            for (ScopeRule r : rules) {
                if (r != null && r.getScopeType() != null) out.add(r.getScopeType());
            }
            return out;
        }
    }
}
