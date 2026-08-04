package io.softa.starter.permission.spi;

import com.fasterxml.jackson.annotation.JsonValue;
import lombok.AllArgsConstructor;
import lombok.Getter;

/**
 * Scope type for data-range filtering.
 * HR-relative types (SELF / DIRECT_REPORTS / DEPT_SUBTREE / MANAGED_DEPARTMENTS)
 * resolve their anchor from the caller's own employee, read from the
 * request context ({@code ContextHolder.getContext().getEmpInfo()}); some also accept
 * an admin-fixed scopeExpr override (see each below). Pure users (no EmpInfo) degrade
 * to a match-none filter on those types. CUSTOM carries an admin-authored Filters array
 * whose dynamic leaf values use env placeholders (USER_ID / USER_EMP_ID / USER_DEPT_ID /
 * USER_COMP_ID) that FilterUnitParser substitutes from the context at SQL-build time.
 *
 * <h3>There is no company scope type, on purpose</h3>
 * A {@code LEGAL_ENTITY} type existed and was removed. It compiled to
 * {@code legalEntityId = USER_COMP_ID} — the company the <i>caller</i> belongs to — so one role
 * behaved differently for each holder: an HR in company A saw all of A's records, the same role in B
 * saw B's. Which companies a role may reach is a property of the role, not of whoever holds it, and it
 * is configured as the data scope on the company model itself, which
 * {@code DefaultPermissionSnapshotProvider.readGrantedCompanyIds} resolves into the grant that bounds
 * every {@code @Model(multiCompany)} read. A rule that genuinely wants the caller's own company can
 * still say so as a CUSTOM rule naming {@code USER_COMP_ID} — explicitly, and visibly per-user.
 *
 * <p>2026-07-14: moved from {@code user-starter} to {@code softa-base} so it can be
 * part of the {@code PermissionInfo}/{@code ScopeRule} snapshot data model shared by
 * {@code user-starter} (build) and {@code permission-starter} (consume).
 */
@Getter
@AllArgsConstructor
public enum ScopeType {
    ALL("All", "No row restriction"),
    SELF("Self", "Only own record (uses employeeId)"),
    DIRECT_REPORTS("DirectReports", "Direct reports (uses employeeId as manager_id)"),
    DEPT_SUBTREE("DeptSubtree", "Subtree of the caller's own department, or a specific department when scopeExpr.deptId is set"),
    MANAGED_DEPARTMENTS("ManagedDepartments", "Departments managed by user (scopeExpr.deptIds optional)"),
    CREATED_BY_SELF("CreatedBySelf", "Rows created by the current user (createdId = current user id; works for pure users too)"),
    CUSTOM("Custom", "Custom filter expression (scopeExpr is a Filters array; env-placeholder values are resolved at SQL time)")
    ;

    @JsonValue
    private final String code;

    private final String description;
}
