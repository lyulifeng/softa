package io.softa.starter.permission.aspect;

import java.io.Serializable;
import java.lang.reflect.Method;
import java.lang.reflect.Parameter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.springframework.core.annotation.AnnotatedElementUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.annotation.RequestMapping;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.service.PermissionService;
import lombok.RequiredArgsConstructor;

/**
 * Enforces {@link io.softa.starter.permission.annotation.MainModelScope}:
 * verify the caller's row scope on the endpoint's main model, THEN open the
 * narrow {@code Context.skipDataScope} bypass for the endpoint body.
 *
 * <h3>Order is the security property</h3>
 * The main-model check runs while the flag is still CLOSED, so
 * {@code checkIdsAccess} sees the caller's real scope; the flag opens only
 * after every declared scope has passed — same discipline as
 * {@code @RequireRole} ("enabled ONLY after verified — never before") — and
 * is restored in a finally block, so the bypass cannot leak past this call.
 *
 * <h3>What the bypass is NOT</h3>
 * {@code skipDataScope} bypasses row scope only (scope filters + id checks).
 * Sensitive-field masking and write-payload guards keep running inside the
 * endpoint — that is the difference from {@code skipPermissionCheck}, and
 * the reason this aspect does not reuse it.
 *
 * <h3>Filter rewrite, not filter check</h3>
 * A declared {@code filterParam} argument is REPLACED with itself AND-ed with
 * the caller's scope ({@code appendScopeAccessFilters}) before proceeding.
 * With the flag then open, the service's own {@code searchList} won't stack a
 * second scope on top — the entry rewrite is the single application.
 *
 * <p>Resolution (model inference, parameter lookup) is cached per method and
 * validated at startup by {@link MainModelScopeStartupValidator}; a resolution
 * failure here is therefore a programming error, not a request-time condition.
 */
@Aspect
@Component
@RequiredArgsConstructor
public class MainModelScopeAspect {

    private final PermissionService permissionService;

    private final Map<Method, List<ResolvedScope>> cache = new ConcurrentHashMap<>();

    /** One declared scope, resolved against the concrete method. */
    record ResolvedScope(String model, int idIndex, int filterIndex) {
    }

    @Around("@annotation(io.softa.starter.permission.annotation.MainModelScope)"
            + " || @annotation(io.softa.starter.permission.annotation.MainModelScopes)")
    public Object enforce(ProceedingJoinPoint jp) throws Throwable {
        Method method = ((MethodSignature) jp.getSignature()).getMethod();
        List<ResolvedScope> scopes = cache.computeIfAbsent(method, MainModelScopeAspect::resolve);
        Object[] args = jp.getArgs();

        // ── ① verify / rewrite — flag still closed, real scope applies
        for (ResolvedScope scope : scopes) {
            if (scope.idIndex() >= 0) {
                Collection<? extends Serializable> ids = idsOf(args[scope.idIndex()]);
                // Fail closed on a null/empty id argument. checkIdsAccess returns
                // silently for an empty collection, so letting it through would
                // open the bypass with NOTHING verified — and an endpoint that can
                // locate data by other parameters (a code, an employeeId) would
                // then be reachable at full range simply by omitting the id.
                // An endpoint whose id is legitimately optional must not declare
                // idParam for it — declare filterParam or split the endpoint.
                if (ids.isEmpty()) {
                    throw new PermissionException("Parameter '"
                            + method.getParameters()[scope.idIndex()].getName()
                            + "' is required: the main-model scope check on "
                            + scope.model() + " has nothing to verify without it.");
                }
                // READ is the API's documented default; row scope carries no
                // read/write direction, so no value could check differently —
                // which is why the annotation has no accessType attribute.
                permissionService.checkIdsAccess(scope.model(), ids, AccessType.READ);
            }
            if (scope.filterIndex() >= 0) {
                Filters original = (Filters) args[scope.filterIndex()];
                args[scope.filterIndex()] = permissionService.appendScopeAccessFilters(
                        scope.model(), original == null ? new Filters() : original);
            }
        }

        // ── ② bypass — only now, and only row scope
        if (!ContextHolder.existContext()) {
            // Unbound context (scheduler / MQ threads): shouldBypass() is already
            // true downstream, the flag would land on a throwaway Context anyway.
            return jp.proceed(args);
        }
        Context ctx = ContextHolder.getContext();
        boolean previous = ctx.isSkipDataScope();
        try {
            ctx.setSkipDataScope(true);
            return jp.proceed(args);
        } finally {
            ctx.setSkipDataScope(previous);
        }
    }

    // ─────────────────────── resolution ───────────────────────

    /**
     * Resolve every declared {@code @MainModelScope} against the method:
     * infer the model when omitted, locate the named parameters. Throws
     * {@link IllegalStateException} with the concrete fix on any mismatch —
     * called at startup by the validator, so misconfiguration fails the boot,
     * not the first request.
     */
    static List<ResolvedScope> resolve(Method method) {
        var declared = method.getAnnotationsByType(
                io.softa.starter.permission.annotation.MainModelScope.class);
        if (declared.length == 0) {
            // The pointcut matched but this Method carries no resolvable
            // annotation (interface-proxied / bridge method). Proceeding would
            // open the bypass with zero checks — fail instead. If this fires,
            // move the annotation onto the method the proxy actually exposes.
            throw new IllegalStateException("@MainModelScope pointcut matched " + method
                    + " but no annotation is resolvable on it — refusing to open the"
                    + " scope bypass unchecked.");
        }
        List<ResolvedScope> out = new ArrayList<>(declared.length);
        for (var scope : declared) {
            String model = scope.model().isEmpty() ? inferModel(method) : scope.model();
            int idIndex = scope.idParam().isEmpty() ? -1
                    : paramIndex(method, scope.idParam(), null);
            int filterIndex = scope.filterParam().isEmpty() ? -1
                    : paramIndex(method, scope.filterParam(), Filters.class);
            if (idIndex < 0 && filterIndex < 0) {
                throw new IllegalStateException("@MainModelScope on " + method
                        + " declares neither idParam nor filterParam — nothing to check."
                        + " Declare at least one, or drop the annotation.");
            }
            out.add(new ResolvedScope(model, idIndex, filterIndex));
        }
        return out;
    }

    /**
     * Infer the main model from the controller's class-level request mapping:
     * {@code /LeaveRequest/...} → {@code LeaveRequest}. Aggregate controllers
     * whose path does not start with a model name must declare {@code model}
     * explicitly — path conventions are the same ones {@code EndpointIndex}
     * builds its URI index from, so a path that lies here lies to Layer A too.
     */
    private static String inferModel(Method method) {
        RequestMapping mapping = AnnotatedElementUtils.findMergedAnnotation(
                method.getDeclaringClass(), RequestMapping.class);
        String[] paths = mapping == null ? new String[0] : mapping.path();
        for (String path : paths) {
            for (String segment : path.split("/")) {
                if (!segment.isEmpty() && !segment.startsWith("{")) {
                    return segment;
                }
            }
        }
        throw new IllegalStateException("@MainModelScope on " + method
                + " has no model and the declaring class has no request-mapping"
                + " path to infer it from. Declare model=\"...\" explicitly.");
    }

    private static int paramIndex(Method method, String name, Class<?> requiredType) {
        Parameter[] parameters = method.getParameters();
        for (int i = 0; i < parameters.length; i++) {
            if (parameters[i].getName().equals(name)) {
                Class<?> type = parameters[i].getType();
                if (requiredType == null && type.isArray() && type.componentType().isPrimitive()) {
                    throw new IllegalStateException("@MainModelScope on " + method
                            + ": idParam '" + name + "' is a primitive array (" + type.getSimpleName()
                            + ") — its elements cannot be read as ids. Use Long[] / List<Long>.");
                }
                if (requiredType != null && !requiredType.isAssignableFrom(parameters[i].getType())) {
                    throw new IllegalStateException("@MainModelScope on " + method
                            + ": parameter '" + name + "' is " + parameters[i].getType().getSimpleName()
                            + ", but only " + requiredType.getSimpleName() + " can be scope-rewritten."
                            + " Ad-hoc DTO/Map conditions must be filtered manually in the service.");
                }
                return i;
            }
        }
        throw new IllegalStateException("@MainModelScope on " + method
                + ": no parameter named '" + name + "'. Available: "
                + Arrays.stream(parameters).map(Parameter::getName).toList()
                + " (parameter names require the -parameters compiler flag).");
    }

    /** Accepts a single id, a collection of ids, or an array of ids. */
    @SuppressWarnings("unchecked")
    private static Collection<? extends Serializable> idsOf(Object arg) {
        if (arg == null) return List.of();
        if (arg instanceof Collection<?> c) return (Collection<? extends Serializable>) c;
        if (arg instanceof Object[] a) {
            return Arrays.stream(a).map(Serializable.class::cast).toList();
        }
        return List.of((Serializable) arg);
    }
}
