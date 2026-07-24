package io.softa.starter.permission.interceptor;

import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.SystemRole;
import io.softa.framework.base.exception.ConfigurationException;
import io.softa.framework.base.exception.PermissionException;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.index.EndpointIndex;

/**
 * Endpoint gate — request-level access control.
 *
 * Flow:
 *   1. Match request URI against permission.public-uri-patterns (yml).
 *      Public endpoints (login / health / oauth callback) are allowed without auth.
 *   2. Short-circuit when caller is super-admin (system role).
 *   3. EndpointIndex.lookup(uri, method) → permissionId.
 *      Unmapped endpoints → 403 (defaults to denying unknown URLs).
 *   4. PermissionInfo.permissions.contains(permissionId) → 403 when missing.
 *
 * The row-scope filter (ScopeFilterAspect) and response field mask
 * (FieldFilter) run after this passes.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class PermissionInterceptor implements HandlerInterceptor {

    private final AntPathMatcher matcher = new AntPathMatcher();
    private final EndpointIndex endpointIndex;
    private final PermissionSnapshotProvider snapshotProvider;
    /** Whitelist patterns bound via {@code @ConfigurationProperties} — see
     *  {@link PermissionInterceptorProperties} for why this isn't
     *  {@code @Value} (YAML list binding). */
    private final PermissionInterceptorProperties properties;

    @Override
    public boolean preHandle(HttpServletRequest req, HttpServletResponse res, Object handler) {
        // Use servletPath (path INSIDE the app context) so the EndpointIndex
        // convention is app-context-agnostic. With server.servlet.context-path
        // = "/api/hcm":
        //   getRequestURI()  → "/api/hcm/Employee/searchPage"  (full URL)
        //   getServletPath() → "/Employee/searchPage"          (in-app path)
        // Public URI patterns also match against this in-app path so the yml
        // patterns don't have to be rewritten per-app.
        String uri = req.getServletPath();
        String method = req.getMethod();

        if (isPublic(uri)) return true;

        // Auth context populated by upstream filter (softa-web)
        Context ctx = ContextHolder.getContext();
        if (ctx == null || ctx.getUserId() == null) {
            throw new PermissionException("Authentication required for " + uri);
        }
        // Require tenantId before we cache PermissionInfo by (tenantId, userId).
        // Without this, a request whose upstream forgot to populate tenantId
        // produces a cache key of `perm:null:user:<id>` — a slot every
        // tenant collides on. Fail-closed and loudly so the upstream auth
        // misconfiguration is caught immediately.
        if (ctx.getTenantId() == null) {
            // Distinct exception type so monitoring can separate "user
            // lacks permission" (PermissionException — 403 user-facing)
            // from "auth context broken" (ConfigurationException — 5xx /
            // alertable). Both fail the request, but the operator signal
            // is different.
            log.error("Missing tenantId on authenticated request — userId={}, uri={}",
                    ctx.getUserId(), uri);
            throw new ConfigurationException("Authentication missing tenant context for " + uri);
        }

        // Authenticated-bypass: caller IS logged in (above check passed) but the
        // endpoint is exempt from the permission gate. Used for "self-service"
        // endpoints every user needs: /UserProfile/getMy*, /me/**,
        // /UserAccount/logout, /UserAccount/changeMyPassword, etc. Returned BEFORE
        // any snapshot work — bypassed endpoints don't consume the snapshot (and
        // /me builds its own on a cache miss), so there's no reason to read/build
        // it for them. Role codes are intentionally NOT bridged on bypass paths,
        // so {@code @RequireRole} on a whitelisted path still fails closed.
        if (matchAny(properties.getAuthenticatedBypassPatterns(), uri)) return true;

        // Non-bypass: build (cache-aside via the SnapshotProvider) the per-user
        // snapshot the gate needs. This same read warms the user's cache for any
        // later request.
        PermissionInfo pi = snapshotProvider.get(ctx.getTenantId(), ctx.getUserId());

        // Super-admin bypass — role-based, single source of truth. Bridge the
        // resolved role codes into the framework-layer Context so framework
        // aspects (e.g. {@code @RequireRole}) can gate on system roles without
        // depending on the user-starter permission model.
        bridgeRoleCodesToContext(ctx, pi);
        // Platform super-admin — full bypass, cross-tenant (crossTenant set in the bridge).
        if (PermissionInfo.isSuperAdmin(pi)) return true;
        // Tenant super-admin — bypasses the permission gate WITHIN its own tenant (tenant-isolated,
        // crossTenant stays false), but is denied platform-only Ops endpoints (billing / plan /
        // provisioning) which only SUPER_ADMIN may reach.
        if (PermissionInfo.isTenantAdmin(pi)) {
            if (matchAny(properties.getPlatformOnlyPatterns(), uri)) {
                log.warn("Platform-only endpoint denied to tenant-admin — userId={}, uri={} {}",
                        ctx.getUserId(), method, uri);
                throw new PermissionException("Platform-admin only: " + method + " " + uri);
            }
            return true;
        }

        // EndpointIndex.lookup returns every permission id that lists this
        // endpoint in its `permission.endpoints` array (or matches the
        // standard CRUD derivation). The user is allowed if their permission
        // set intersects this candidate set — ANY granted permission opens
        // the endpoint. This is how shared lookup endpoints (e.g.
        // /Department/searchList used by both the Department admin page and
        // the Employee page's dept-tree panel) get reachable from multiple
        // business permissions.
        Set<String> candidatePermissions = endpointIndex.lookup(uri, method);
        if (candidatePermissions == null || candidatePermissions.isEmpty()) {
            throw new PermissionException("Endpoint not registered: " + method + " " + uri);
        }
        Set<String> userPermissions = pi.getPermissions();
        if (userPermissions == null || Collections.disjoint(userPermissions, candidatePermissions)) {
            // Detail (required-permission set) goes to server log so ops can
            // diagnose; the response carries only "missing permission for X"
            // so a probing client can't enumerate the permission graph by
            // poking endpoints and reading 403 bodies.
            log.warn("Missing permission — userId={}, uri={} {}, required any of: {}",
                    ctx.getUserId(), method, uri, candidatePermissions);
            throw new PermissionException("Missing permission for " + method + " " + uri);
        }
        return true;
    }

    /**
     * Copy the resolved role codes onto the framework-layer
     * {@link PermissionInfo} carried by the
     * Context, so framework aspects can evaluate {@code @RequireRole} without
     * importing the user-starter permission model (the Context field is the
     * decoupling SPI). Super-admin is expanded to hold every {@link SystemRole}
     * code — god-mode already short-circuits every other layer, so a
     * system-role gate must not be stricter for it.
     */
    private void bridgeRoleCodesToContext(Context ctx, PermissionInfo pi) {
        Set<String> codes = new HashSet<>();
        if (pi != null && pi.getRoleCodes() != null) codes.addAll(pi.getRoleCodes());
        if (PermissionInfo.isSuperAdmin(pi)) {
            for (SystemRole r : SystemRole.values()) codes.add(r.getCode());
            // Platform super-admin operates cross-tenant (sees every tenant's data + the
            // non-tenant-filtered Ops models). A TENANT_ADMIN does NOT get this — it stays
            // tenant-isolated (crossTenant remains false).
            ctx.setCrossTenant(true);
        }
        ctx.setRoleCodes(codes);
    }

    private boolean isPublic(String uri) {
        return matchAny(properties.getPublicUriPatterns(), uri);
    }

    private boolean matchAny(List<String> patterns, String uri) {
        if (patterns == null) return false;
        for (String pattern : patterns) {
            if (pattern == null || pattern.isEmpty()) continue;
            if (matcher.match(pattern, uri)) return true;
        }
        return false;
    }
}
