package io.softa.framework.orm.aspect;

import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.rpc.RemoteApiClient;

/**
 * Routes a model data operation to the application that owns the model, reusing the
 * framework model RPC.
 *
 * <p>Routing keys on the model's {@code appCode} (app identity, projected from
 * {@code sys_model.app_code}): an operation on a model whose {@code appCode} differs from
 * this runtime's {@code system.app-code} is routed to the owning app via
 * {@link RemoteApiClient#modelRPC}; otherwise it proceeds locally. Routing is therefore
 * automatic and correct-by-default — there is no per-model routing flag to set, and a model
 * can never silently run local because someone forgot to flag it.
 */
@Aspect
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@ConditionalOnProperty(name = "rpc.enable", havingValue = "true")
public class SwitchServiceAspect {

    private final RemoteApiClient apiClient;

    public SwitchServiceAspect(RemoteApiClient apiClient) {
        this.apiClient = apiClient;
    }

    /**
     * The first parameter of the annotated method must be the model name.
     */
    @Around("@within(io.softa.framework.orm.annotation.RPCCheckpoint) || " +
            "@annotation(io.softa.framework.orm.annotation.RPCCheckpoint)")
    public Object around(ProceedingJoinPoint joinPoint) throws Throwable {
        Object[] methodArgs = joinPoint.getArgs();
        if (!(methodArgs[0] instanceof String modelName)) {
            return joinPoint.proceed();
        }
        // System models are shared and never routed.
        if (ModelConstant.SYSTEM_MODEL.contains(modelName)) {
            return joinPoint.proceed();
        }
        String ownerAppCode = ModelManager.getModel(modelName).getAppCode();
        String selfAppCode = SystemConfig.env.getAppCode();
        // Local when the model has no owning app, or it belongs to this runtime's app.
        if (StringUtils.isBlank(ownerAppCode) || ownerAppCode.equals(selfAppCode)) {
            return joinPoint.proceed();
        }
        // Remote: route to the owning app.
        String methodName = joinPoint.getSignature().getName();
        return apiClient.modelRPC(ownerAppCode, modelName, methodName, methodArgs);
    }

}
