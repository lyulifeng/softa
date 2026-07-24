package io.softa.starter.permission.spi;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import tools.jackson.databind.JsonNode;


/**
 * One row of role_navigation.data_scopes JSON array.
 *
 * scopeExpr semantics depend on scopeType:
 *   - ALL / SELF / DIRECT_REPORTS:   null
 *   - DEPT_SUBTREE:                  required, {"deptId": "..."}
 *   - LEGAL_ENTITY:                  optional, {"legalEntityId": "..."} (null = dynamic from ec)
 *   - MANAGED_DEPARTMENTS:           optional, {"deptIds": ["..."]} (null = dynamic from empInfo.managedDeptIds)
 *   - CUSTOM:                        required FilterCondition; values may reference $principal.xxx
 *
 * <p>2026-07-14: moved from {@code user-starter} to {@code softa-base} — it is part of
 * the {@code PermissionInfo} snapshot data model, shared by build (user-starter) and
 * enforce (permission-starter).
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "Data scope rule")
public class ScopeRule {

    @Schema(description = "Scope type")
    private ScopeType scopeType;

    @Schema(description = "Scope expression; semantics depend on scopeType (see class doc)")
    private JsonNode scopeExpr;

    /**
     * Convenience accessor: pulls a top-level field name from scopeExpr (object form).
     */
    public JsonNode scopeExprField(String fieldName) {
        if (scopeExpr == null || !scopeExpr.isObject()) return null;
        return scopeExpr.get(fieldName);
    }
}
