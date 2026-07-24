package io.softa.starter.user.controller;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.Data;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.ModelService;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.dto.UserRef;
import io.softa.starter.user.util.ModelRefIds;
import io.softa.starter.user.util.PermissionSnapshotKey;

/**
 * Read-only admin API for the user-access (RBAC) management UI — the endpoints
 * the FE {@code user/access} section calls that are NOT plain entity CRUD.
 * Served under {@code /userAccess} (renamed from the generic {@code /admin}; the
 * whole surface — FE {@code src/app/user/access} + this {@code user-starter}
 * controller — is user-module scoped). Sibling {@code NavigationConfigOptionsController}
 * shares the prefix for the role-wizard option endpoints.
 *
 * <h3>{@code GET /userAccess/userRefs}</h3>
 * {@link UserRef} rows for the Add-Members / AssignRoles dialogs: the UserAccount
 * auth identity plus its Organizational identity (employeeId / departmentId /
 * legalEntityId) when the user is linked to an Employee — joined on the BE with
 * one extra IN query so the dialog classifies role-compatibility correctly
 * (reading UserAccount directly made every user look "pure"). Two indexed
 * queries, ~10ms typical. Rows are read as {@code Map} (not the entity overload)
 * to sidestep a JsonNode/Long conversion bug on snowflake audit-id columns.
 *
 * <h3>{@code GET /userAccess/userEffectivePermissions}</h3>
 * The effective permission snapshot for an ARBITRARY user, read straight from the
 * shared cache as raw JSON (the permission engine is the sole builder; it warms a
 * user's entry on that user's own authenticated requests). {@code GET /me/uiContext}
 * serves the CURRENT user the same way. A target user who has not been active since
 * the last cache expiry returns {@code null} here — an accepted degradation for this
 * admin view. The key is scoped by the caller's tenantId (no cross-tenant leak).
 *
 * <h3>Org identity</h3>
 * The employeeId / departmentId / legalEntityId columns come from reading the
 * {@code Employee} model directly (约定读). {@code /userAccess/*} is super-admin
 * only, so the read isn't scope-filtered and needs no permission bypass; a
 * deployment with no {@code Employee} model degrades to pure UserAccount rows.
 */
@Slf4j
@Tag(name = "User Access")
@RestController
@RequestMapping("/userAccess")
@RequiredArgsConstructor
public class UserAccessController {

    /** Max UserAccount rows returned — matches the dialog's client-side
     *  pagination so the BE never silently truncates. */
    private static final int USER_PAGE_CAP = 1000;

    private final ModelService<?> modelService;
    private final CacheService cacheService;

    // ─────────────────────── user refs (member / assign dialogs) ───────────────────────

    @GetMapping("/userRefs")
    @Operation(summary = "List user refs (UserAccount + org identity) for admin dialogs")
    public ApiResponse<List<UserRef>> listUserRefs() {
        FlexQuery q = new FlexQuery();
        q.setLimitSize(USER_PAGE_CAP);
        q.setFields(List.of(
                "id", "nickname", "username", "email", "mobile",
                "status", "createdTime", "updatedTime"));
        List<Map<String, Object>> users = modelService.searchList("UserAccount", q);

        Map<Long, EmployeeOrgView> ctxByUser = loadOrgContext(users);

        List<UserRef> out = new ArrayList<>(users.size());
        for (Map<String, Object> u : users) {
            Long id = ModelRefIds.extractLongId(u.get("id"));
            EmployeeOrgView ctx = ctxByUser.get(id);
            out.add(new UserRef(
                    id,
                    asString(u.get("nickname")),
                    asString(u.get("username")),
                    asString(u.get("email")),
                    asString(u.get("mobile")),
                    asString(u.get("status")),
                    asString(u.get("createdTime")),
                    asString(u.get("updatedTime")),
                    ctx == null ? null : ctx.getId(),
                    ctx == null ? null : ctx.getDepartmentId(),
                    ctx == null ? null : ctx.getLegalEntityId()));
        }
        return ApiResponse.success(out);
    }

    /** Resolve employeeId / departmentId / legalEntityId per user by reading the
     *  {@code Employee} model directly (约定读). Empty map when no {@code Employee}
     *  model exists (non-HR deployment) or on error — degrades to "every user is
     *  pure". {@code /userAccess/*} is super-admin only, so the read is not
     *  scope-filtered (super-admin bypasses) and needs no permission skip. */
    private Map<Long, EmployeeOrgView> loadOrgContext(List<Map<String, Object>> users) {
        if (users.isEmpty() || !ModelManager.existModel("Employee")) return Map.of();
        Set<Long> userIds = new HashSet<>(users.size());
        for (Map<String, Object> u : users) {
            Long id = ModelRefIds.extractLongId(u.get("id"));
            if (id != null) userIds.add(id);
        }
        if (userIds.isEmpty()) return Map.of();
        List<EmployeeOrgView> rows;
        try {
            rows = modelService.searchList("Employee",
                    new FlexQuery(List.of("userId", "id", "departmentId", "legalEntityId"),
                            new Filters().in("userId", userIds)),
                    EmployeeOrgView.class);
        } catch (Throwable t) {
            log.warn("userRefs — Employee read failed; degrading to no org context", t);
            return Map.of();
        }
        Map<Long, EmployeeOrgView> out = new HashMap<>(rows.size());
        for (EmployeeOrgView e : rows) {
            if (e.getUserId() != null) out.put(e.getUserId(), e);
        }
        return out;
    }

    private static String asString(Object v) {
        return v == null ? null : v.toString();
    }

    /** {@code Employee} projection for org-identity enrichment. {@code id} is the
     *  employeeId; department / legalEntity may be null. Public + no-arg ctor so
     *  the ModelService Class projection (BeanTool) can populate it. */
    @Data
    public static class EmployeeOrgView {
        private Long userId;
        private Long id;
        private Long departmentId;
        private Long legalEntityId;
    }

    // ─────────────────────── effective permissions (user detail view) ───────────────────────

    @GetMapping("/userEffectivePermissions")
    @Operation(summary = "Effective permission snapshot (nav / permission / data-scope / SFS) for a user")
    public ApiResponse<JsonNode> userEffectivePermissions(@RequestParam("userId") Long userId) {
        Context ctx = ContextHolder.getContext();
        Long tenantId = ctx == null ? null : ctx.getTenantId();
        // Read the target user's cached snapshot as raw JSON. The permission engine
        // builds it on that user's own authenticated requests; this endpoint no
        // longer builds (keeping user-starter engine-free), so a user who has not
        // been active since the last cache expiry returns null — accepted here.
        JsonNode snapshot = cacheService.get(PermissionSnapshotKey.forUser(tenantId, userId), JsonNode.class);
        return ApiResponse.success(snapshot);
    }
}
