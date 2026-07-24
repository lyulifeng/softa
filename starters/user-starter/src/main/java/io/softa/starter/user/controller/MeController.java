package io.softa.starter.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import lombok.RequiredArgsConstructor;
import tools.jackson.databind.JsonNode;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.service.impl.UiContextBuilder;
import io.softa.starter.user.util.PermissionSnapshotKey;

/**
 * /me endpoints — the current user's UI context.
 *
 * <p>Reads the permission snapshot from the shared cache as raw JSON (the engine —
 * permission-starter — is the authoritative builder + writer). On a cache MISS
 * (Redis blip, or the engine cache not yet warmed by a gated request), falls back
 * to {@link UiContextBuilder}, which assembles the same shape from user-starter's
 * OWN RBAC entities — so {@code /me/uiContext} is self-sufficient and never returns
 * a bare {@code null} to the bootstrap. Returning JSON keeps user-starter free of
 * any dependency on the engine's {@code PermissionInfo} type.
 *
 * <p>Frontend Sidebar / RouteGuard / business pages all consume this single
 * payload via the {@code useUIContext} hook (cached 30s; 401 triggers refetch).
 */
@Tag(name = "Me")
@RestController
@RequestMapping("/me")
@RequiredArgsConstructor
public class MeController {

    private final CacheService cacheService;
    private final UiContextBuilder uiContextBuilder;

    @GetMapping("/uiContext")
    @Operation(summary = "Get the current user's UI context (navigations, permissions, sensitive field sets)")
    public ApiResponse<JsonNode> uiContext() {
        Context ctx = ContextHolder.getContext();
        JsonNode info = cacheService.get(
                PermissionSnapshotKey.forUser(ctx.getTenantId(), ctx.getUserId()), JsonNode.class);
        if (info == null || info.isNull()) {
            // Engine cache cold — build the same shape from user-starter's own
            // RBAC entities so the bootstrap always gets a payload.
            info = uiContextBuilder.build(ctx.getUserId());
        }
        return ApiResponse.success(info);
    }
}
