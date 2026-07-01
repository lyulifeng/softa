package io.softa.framework.orm.rpc;

public interface RemoteApiClient {

    /**
     * Invoke a model operation on the app that owns the model.
     *
     * @param appCode the owning app's appCode (its {@code system.app-code}); doubles as the
     *                {@code rpc.services} registry key used to resolve the target endpoint
     */
    <T> T modelRPC(String appCode, String modelName, String methodName, Object[] methodArgs);

}