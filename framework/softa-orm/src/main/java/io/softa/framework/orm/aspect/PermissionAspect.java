package io.softa.framework.orm.aspect;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.orm.annotation.SwitchUser;
import lombok.extern.slf4j.Slf4j;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.stereotype.Component;

/**
 * Aspect for permission check.
 */
@Slf4j
@Aspect
@Component
public class PermissionAspect {

    /**
     * Around aspect with SkipPermissionCheck annotation.
     * Do not check permission from the annotated method, but the context user still keeps the current user.
     *
     * <h3>ScopedValue-binding requirement (Known-Issues Lat2)</h3>
     * {@code ContextHolder.getContext()} returns a fresh default Context
     * when no {@link ScopedValue} binding exists on the current thread
     * ({@link ContextHolder#existContext()} returns {@code false}). Our
     * {@code setSkipPermissionCheck(true)} then mutates that transient
     * instance — the mutation is discarded the moment this aspect returns,
     * because the outer caller sees a new default Context on its next
     * {@code getContext()} call. Net effect: {@code @SkipPermissionCheck}
     * is a silent no-op, downstream {@code PermissionServiceImpl} still
     * enforces scope / SFS / write guards. This is a legit failure mode
     * for callers who forgot to wrap in
     * {@code ContextHolder.runWith(...) / callWith(...)}, and used to be
     * silent.
     *
     * <p>Log a WARN so ops can trace it — but do not throw. Legit code
     * paths at framework boot / lifecycle events may hit this before the
     * request-scoped context is bound; a throw would break them, whereas
     * the intended semantics (skip check) simply falls back to "check as
     * usual" which is safe.
     * @param joinPoint Around join point object
     * @return Original method return value
     * @throws Throwable Exception
     */
    @Around("@annotation(io.softa.framework.orm.annotation.SkipPermissionCheck)")
    public Object skipPermissionCheck(ProceedingJoinPoint joinPoint) throws Throwable {
        if (!ContextHolder.existContext()) {
            log.warn("@SkipPermissionCheck on {} runs outside a bound ContextHolder "
                    + "ScopedValue — mutation is discarded and the annotation is a no-op. "
                    + "Wrap the caller in ContextHolder.runWith(bootstrapCtx, ...) or "
                    + "ContextHolder.callWith(...).",
                    joinPoint.getSignature().toShortString());
        }
        Context context = ContextHolder.getContext();
        boolean previousValue = context.isSkipPermissionCheck();
        try {
            context.setSkipPermissionCheck(true);
            return joinPoint.proceed();
        } finally {
            context.setSkipPermissionCheck(previousValue);
        }
    }

    /**
     * RequireRole annotation aspect.
     * Check if the current user has the specified role permission.
     */
    @Around("@annotation(io.softa.framework.orm.annotation.RequireRole)")
    public Object requireRole(ProceedingJoinPoint joinPoint) throws Throwable {
        Context context = ContextHolder.getContext();
        // TODO: User role check, if the user does not have the specified role, throw an exception.
        // Skip permission check after role check.
        boolean previousIgnoreValue = context.isSkipPermissionCheck();
        try {
            context.setSkipPermissionCheck(true);
            return joinPoint.proceed();
        } finally {
            context.setSkipPermissionCheck(previousIgnoreValue);
        }
    }

    /**
     * Switch current user to the specified system level user, in order to access the system resources.
     */
    @Around("@annotation(switchUser)")
    public Object switchUser(ProceedingJoinPoint joinPoint, SwitchUser switchUser) throws Throwable {
        Context clonedContext = ContextHolder.cloneContext();
        String userName = switchUser.alias().isBlank() ? switchUser.value().getName() : switchUser.alias();
        clonedContext.setName(userName);
        // Skip permission check for system level users.
        clonedContext.setSkipPermissionCheck(true);
        // Switch context to the cloned context.
        return ContextHolder.callWith(clonedContext, joinPoint::proceed);
    }

}
