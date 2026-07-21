package io.softa.starter.user.dto;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.utils.JsonUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Confirms the cache-HIT path of {@code GET /me/uiContext}. The engine (permission-starter) caches a
 * serialized {@code PermissionInfo}; MeController reads it back with {@code CacheService.get(key,
 * UiContext.class)}, which is exactly {@code JsonUtils.stringToObject(json, UiContext.class)}
 * (CacheServiceImpl). user-starter can't reference {@code PermissionInfo} (⊥ permission-starter), so this
 * feeds a hand-built JSON mirroring the engine's serialized shape — INCLUDING the fields {@link UiContext}
 * deliberately drops ({@code permissionCodes}, and {@code modelScopeMap} with its nested {@code ScopeRule})
 * — and asserts the FE-facing fields populate, the extras are ignored (no throw), {@code entitledModules}
 * stays null. The unit-context mapper has {@code FAIL_ON_UNKNOWN_PROPERTIES} on, so passing here proves the
 * {@code @JsonIgnoreProperties(ignoreUnknown = true)} contract holds even under the strict setting.
 */
class UiContextTest {

    /** The exact shape the engine writes to the cache (a serialized {@code PermissionInfo}). */
    private static final String ENGINE_PERMISSION_INFO_JSON = """
            {
              "roleCodes": ["HR"],
              "permissionCodes": {"HR": ["employee.view"]},
              "navigations": ["hr.employee", "hr"],
              "permissions": ["employee.view", "employee.create"],
              "modelScopeMap": {"Employee": [{"scopeType": "SELF", "scopeExpr": null}]},
              "modelSensitiveFieldSetsMap": {"Employee": ["comp"]}
            }""";

    @Test
    void cacheHit_deserializesEngineSnapshot_droppingServerOnlyFields() {
        // The real cache-read path — throws if the extra engine-only fields aren't ignored.
        UiContext ui = JsonUtils.stringToObject(ENGINE_PERMISSION_INFO_JSON, UiContext.class);

        assertThat(ui).isNotNull();
        assertThat(ui.getRoleCodes()).containsExactly("HR");
        assertThat(ui.getNavigations()).containsExactlyInAnyOrder("hr.employee", "hr");
        assertThat(ui.getPermissions()).containsExactlyInAnyOrder("employee.view", "employee.create");
        assertThat(ui.getModelSensitiveFieldSetsMap()).containsOnlyKeys("Employee");
        assertThat(ui.getModelSensitiveFieldSetsMap().get("Employee")).containsExactly("comp");
        // permissionCodes / modelScopeMap are silently dropped (not fields of UiContext); no exception.
        // entitledModules is not in the cache JSON → null → FE applies no version gating.
        assertThat(ui.getEntitledModules()).isNull();
    }
}
