package io.softa.starter.user.aspect;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.aspectj.lang.annotation.Pointcut;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import io.softa.framework.base.context.ContextHolder;
import static io.softa.framework.base.context.ContextUtils.inSystemContext;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.ModelService;

/**
 * A built-in role's access is fixed: a role carrying a {@code code} cannot have its definition or its
 * grants written through the API. Assigning and unassigning users is the one change that stays open.
 *
 * <h3>Why an aspect on {@link ModelService} and not a controller check</h3>
 * The grants are three ordinary models — {@code RoleNavigation} (which pages), {@code RoleDataScope}
 * (which rows), {@code RoleSensitiveFieldSet} (which fields unmasked) — and every model in this
 * framework is writable through two HTTP surfaces at once: its typed {@code EntityController} and the
 * generic {@code ModelController} mapped at {@code /{modelName}}. Between them that is a dozen-odd
 * write verbs ({@code createOne} / {@code createList} / {@code updateOne} / {@code updateList} /
 * {@code updateByFilter} / {@code deleteById} / {@code deleteByIds} / {@code deleteByFilters} /
 * {@code copyById} / the {@code …AndFetch} variants), and a guard written per controller has to
 * enumerate all of them and be extended again every time the framework adds one — a miss is silent
 * and indistinguishable from a deliberate exemption.
 *
 * <p>They all funnel through {@link ModelService}, and so does {@code EntityService}, so this is the
 * one place that sees every write regardless of how it arrived.
 *
 * <p>What was reachable before this existed, on a live tenant: {@code POST /RoleDataScope/updateOne}
 * naming the EMPLOYEE role's row on the {@code Employee} model and setting {@code scopeType: ALL} —
 * one call, and every employee in that tenant reads every colleague's record. {@code SUPER_ADMIN} and
 * {@code TENANT_ADMIN} happen to be immune (their access is computed at runtime and their static rows
 * are ignored), which is exactly why the hole was easy to miss: the two roles an operator would think
 * to test are the two that do not react.
 *
 * <h3>What still passes</h3>
 * <ul>
 *   <li><b>Membership.</b> {@code UserRoleRel} is deliberately absent from {@link #GUARDED} — adding
 *       and removing users is the point of a built-in role, not a modification of it.</li>
 *   <li><b>Seeding and system maintenance.</b> Anything running with {@code skipPermissionCheck} is
 *       let through: that is what pre-data loading sets on both its tenant and system paths, and what
 *       the entitlement downgrade cleanup runs under when it strips over-plan grants. A request
 *       arriving from a user never carries it — not even a super admin's, whose bypass is expressed
 *       through {@code PermissionInfo.isAdmin}, not through this flag.</li>
 * </ul>
 *
 * <p>{@code Role} itself is guarded here too, although {@code RoleServiceImpl.guardSystemMutation}
 * already refuses a system-role edit: that guard only sees callers who go through the typed service,
 * and {@code RoleController} shadows exactly one generic verb ({@code updateOne}). {@code updateList},
 * {@code updateByFilter} and the delete family reached the row unchallenged.
 */
@Slf4j
@Aspect
@Component
public class SystemRoleWriteGuard {

    private static final String ROLE = "Role";
    private static final String ID = "id";
    private static final String CODE = "code";

    /** Guarded model → the field naming the role the row belongs to. */
    private static final Map<String, String> GUARDED = Map.of(
            ROLE, ID,
            "RoleNavigation", "roleId",
            "RoleDataScope", "roleId",
            "RoleSensitiveFieldSet", "roleId");

    /** Deferred: this aspect advises {@code ModelService}, so injecting it directly would be a cycle. */
    private final ObjectProvider<ModelService<?>> modelService;

    public SystemRoleWriteGuard(ObjectProvider<ModelService<?>> modelService) {
        this.modelService = modelService;
    }

    /** Every mutating {@code ModelService} entry point. The model name is the first argument on all of them. */
    @Pointcut("execution(* io.softa.framework.orm.service.ModelService.create*(String, ..))"
            + " || execution(* io.softa.framework.orm.service.ModelService.update*(String, ..))"
            + " || execution(* io.softa.framework.orm.service.ModelService.delete*(String, ..))"
            + " || execution(* io.softa.framework.orm.service.ModelService.copy*(String, ..))")
    void modelWrite() {
    }

    @Before("modelWrite()")
    public void guard(JoinPoint joinPoint) {
        Object[] args = joinPoint.getArgs();
        if (args.length == 0 || !(args[0] instanceof String modelName)) {
            return;
        }
        String roleField = GUARDED.get(modelName);
        if (roleField == null) {
            return;
        }
        if (ContextHolder.getContext() != null && ContextHolder.getContext().isSkipPermissionCheck()) {
            return;
        }
        String method = joinPoint.getSignature().getName();
        if (method.startsWith("create")) {
            guardCreate(modelName, roleField, args);
            return;
        }
        assertNoSystemRole(targetRoleIds(method, modelName, roleField, args));
    }

    /**
     * A create names its role in the payload — except on {@code Role}, where the row has no id yet and
     * what makes it built-in is the {@code code} it is asking for. Reserving the column here matters
     * because everything else in this class keys off it: a caller that could mint its own coded role
     * could then declare any role untouchable.
     */
    private void guardCreate(String modelName, String roleField, Object[] args) {
        List<Map<String, Object>> rows = rowsOf(args.length > 1 ? args[1] : null);
        if (ROLE.equals(modelName)) {
            for (Map<String, Object> row : rows) {
                Object code = row.get(CODE);
                if (code != null && !code.toString().isBlank()) {
                    throw new BusinessException(
                            "Role code is reserved for built-in roles; an admin-created role must have code=null.");
                }
            }
            return;
        }
        assertNoSystemRole(valuesOf(rows, roleField));
    }

    /**
     * Which roles this call would touch.
     *
     * <p>An update is checked on both sides — the role named in the payload and the role the stored row
     * currently belongs to. Only checking the payload would let a grant be dragged off a built-in role;
     * only checking the stored row would let one be dragged onto it.
     */
    private Set<Object> targetRoleIds(String method, String modelName, String roleField, Object[] args) {
        Set<Object> ids = new HashSet<>();
        Object second = args.length > 1 ? args[1] : null;
        if (method.startsWith("update")) {
            List<Map<String, Object>> rows = rowsOf(second);
            // updateByFilter(model, filters, value): the rows are chosen by the filter, and the new
            // values are the third argument rather than the second.
            if (second instanceof Filters filters) {
                ids.addAll(resolveByFilters(modelName, roleField, filters));
                rows = rowsOf(args.length > 2 ? args[2] : null);
            } else {
                ids.addAll(resolveByIds(modelName, roleField, valuesOf(rows, ID)));
            }
            ids.addAll(valuesOf(rows, roleField));
            return ids;
        }
        // delete* / copy* — identified by id, by id list, or by filter.
        if (second instanceof Filters filters) {
            ids.addAll(resolveByFilters(modelName, roleField, filters));
        } else if (second instanceof Collection<?> given) {
            ids.addAll(resolveByIds(modelName, roleField, new ArrayList<>(given)));
        } else if (second != null) {
            ids.addAll(resolveByIds(modelName, roleField, List.of(second)));
        }
        return ids;
    }

    /** The role behind each of these rows. On {@code Role} the ids ARE the role ids — no read needed. */
    private Set<Object> resolveByIds(String modelName, String roleField, Collection<Object> rowIds) {
        if (rowIds.isEmpty()) {
            return Set.of();
        }
        if (ROLE.equals(modelName)) {
            return new HashSet<>(rowIds);
        }
        return resolveByFilters(modelName, roleField, new Filters().in(ID, rowIds));
    }

    /**
     * Read the role ids of the rows a filter selects.
     *
     * <p>System context, so the guard sees the rows rather than the caller's view of them: a scoped read
     * that returns nothing would look exactly like "no built-in role involved" and wave the write past.
     */
    private Set<Object> resolveByFilters(String modelName, String roleField, Filters filters) {
        List<Map<String, Object>> rows = inSystemContext(() ->
                modelService().searchList(modelName, new FlexQuery(List.of(roleField), filters)));
        return valuesOf(rows, roleField);
    }

    /** Refuse the write if any of these roles is built-in, naming the one that stopped it. */
    private void assertNoSystemRole(Set<Object> roleIds) {
        if (roleIds.isEmpty()) {
            return;
        }
        List<Map<String, Object>> roles = inSystemContext(() -> modelService().searchList(
                ROLE, new FlexQuery(List.of(ID, CODE, "name"), new Filters().in(ID, roleIds))));
        for (Map<String, Object> role : roles) {
            Object code = role.get(CODE);
            if (code != null && !code.toString().isBlank()) {
                throw new BusinessException(
                        "Cannot modify built-in role '" + role.get("name") + "' (code=" + code
                                + ") or its access; built-in roles are managed by ops. "
                                + "Only assigning and unassigning users is allowed.");
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static List<Map<String, Object>> rowsOf(Object arg) {
        if (arg instanceof Map) {
            return List.of((Map<String, Object>) arg);
        }
        if (arg instanceof List<?> list) {
            List<Map<String, Object>> rows = new ArrayList<>(list.size());
            for (Object item : list) {
                if (item instanceof Map) {
                    rows.add((Map<String, Object>) item);
                }
            }
            return rows;
        }
        return List.of();
    }

    private static Set<Object> valuesOf(List<Map<String, Object>> rows, String field) {
        Set<Object> values = new HashSet<>();
        for (Map<String, Object> row : rows) {
            Object value = row.get(field);
            if (value != null) {
                values.add(value);
            }
        }
        return values;
    }

    private ModelService<?> modelService() {
        ModelService<?> service = modelService.getIfAvailable();
        if (service == null) {
            // Nothing can be verified, so nothing may pass: this guard is the only thing standing
            // between a built-in role's grants and the generic write API.
            throw new BusinessException("Role write guard unavailable — write refused.");
        }
        return service;
    }
}
