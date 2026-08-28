package io.softa.starter.permission.scope;

import java.util.Set;

import io.softa.framework.base.constant.EnvConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.EmpInfo;
import io.softa.framework.orm.domain.Filters;

/**
 * Fail-closed guard for the {@link EnvConstant} placeholders a compiled scope filter carries.
 *
 * <p>Placeholder VALUES ({@code USER_EMP_ID} / {@code USER_DEPT_ID} / {@code USER_ID} / …) are
 * substituted by {@code FilterUnitParser} when the SQL is built, not at compile time. That parser
 * <b>throws</b> on an {@code EMP_INFO} token with no {@link EmpInfo} bound, so a rule that
 * references one must be dropped here — while it is still a rule — rather than reaching SQL.
 *
 * <p>Dropping the whole rule is the only safe degradation. Resolving a missing value to
 * {@code null} instead would look right for {@code =} (matches nothing) but silently WIDEN a
 * {@code !=} rule to every row.
 *
 * <p>Shared so the two paths that compile scope rules cannot drift: {@link IdentityScopeCompiler}
 * (SELF / DIRECT_REPORTS / CREATED_BY_SELF …) and {@code CustomScopeContributor} (admin-authored
 * CUSTOM filters, which may reference the same tokens).
 */
public final class ScopeEnvGuard {

    private ScopeEnvGuard() {
    }

    /**
     * True if the bound context carries the backing object for every env token in {@code f}.
     * A {@code null} filter needs nothing and passes.
     */
    public static boolean contextSatisfies(Filters f) {
        return f == null || contextSatisfies(ScopeFilterTemplates.envTokens(f));
    }

    /** True if the bound context carries the backing object for every env token the
     *  template needs (EMP_INFO tokens need an {@link EmpInfo}; {@code USER_ID} needs
     *  a userId). NOW/TODAY/YESTERDAY are always resolvable. */
    static boolean contextSatisfies(Set<String> envTokens) {
        if (envTokens.isEmpty()) {
            return true;
        }
        Context ctx = ContextHolder.getContext();
        for (String token : envTokens) {
            if (EnvConstant.USER_ID.equals(token) && (ctx == null || ctx.getUserId() == null)) {
                return false;
            }
            if (EnvConstant.EMP_INFO_PARAMS.contains(token) && (ctx == null || ctx.getEmpInfo() == null)) {
                return false;
            }
        }
        return true;
    }
}
