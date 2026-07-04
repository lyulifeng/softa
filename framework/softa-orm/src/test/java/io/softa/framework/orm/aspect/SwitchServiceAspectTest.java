package io.softa.framework.orm.aspect;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;

import io.softa.framework.base.config.SystemConfig;
import io.softa.framework.orm.constant.ModelConstant;
import io.softa.framework.orm.meta.MetaModel;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.rpc.RemoteApiClient;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockStatic;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Routing decision of {@link SwitchServiceAspect}: a model is routed to its owning app
 * (appCode) only when that differs from this runtime's app; otherwise it runs locally.
 * The owning appCode doubles as the rpc.services registry key passed to RemoteApiClient.modelRPC.
 */
class SwitchServiceAspectTest {

    private static final String SELF_APP = "engine";
    private static final Object LOCAL = "LOCAL";
    private static final Object REMOTE = "REMOTE";

    private RemoteApiClient apiClient;
    private SwitchServiceAspect aspect;
    private SystemConfig originalEnv;

    @BeforeEach
    void setUp() {
        apiClient = mock(RemoteApiClient.class);
        aspect = new SwitchServiceAspect(apiClient);
        originalEnv = SystemConfig.env;
        SystemConfig cfg = new SystemConfig();
        cfg.setAppCode(SELF_APP);
        SystemConfig.env = cfg;
    }

    @AfterEach
    void tearDown() {
        SystemConfig.env = originalEnv;
    }

    private ProceedingJoinPoint pjp(Object[] args) throws Throwable {
        ProceedingJoinPoint pjp = mock(ProceedingJoinPoint.class);
        when(pjp.getArgs()).thenReturn(args);
        when(pjp.proceed()).thenReturn(LOCAL);
        Signature sig = mock(Signature.class);
        when(sig.getName()).thenReturn("update");
        when(pjp.getSignature()).thenReturn(sig);
        return pjp;
    }

    private MetaModel model(String appCode) {
        MetaModel m = mock(MetaModel.class);
        when(m.getAppCode()).thenReturn(appCode);
        return m;
    }

    @Test
    void routesToOwningAppWhenModelBelongsToAnotherApp() throws Throwable {
        ProceedingJoinPoint pjp = pjp(new Object[]{"Order"});
        MetaModel orderModel = model("biz");
        when(apiClient.modelRPC(eq("biz"), eq("Order"), eq("update"), any(Object[].class))).thenReturn(REMOTE);
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModel("Order")).thenReturn(orderModel);
            assertEquals(REMOTE, aspect.around(pjp));
        }
        verify(pjp, never()).proceed();
        verify(apiClient).modelRPC(eq("biz"), eq("Order"), eq("update"), any(Object[].class));
    }

    @Test
    void staysLocalWhenModelBelongsToThisApp() throws Throwable {
        ProceedingJoinPoint pjp = pjp(new Object[]{"Order"});
        MetaModel orderModel = model(SELF_APP);
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModel("Order")).thenReturn(orderModel);
            assertEquals(LOCAL, aspect.around(pjp));
        }
        verify(apiClient, never()).modelRPC(anyString(), anyString(), anyString(), any());
    }

    @Test
    void staysLocalWhenModelHasNoAppCode() throws Throwable {
        ProceedingJoinPoint pjp = pjp(new Object[]{"Order"});
        MetaModel orderModel = model(null);
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            mm.when(() -> ModelManager.getModel("Order")).thenReturn(orderModel);
            assertEquals(LOCAL, aspect.around(pjp));
        }
        verify(apiClient, never()).modelRPC(anyString(), anyString(), anyString(), any());
    }

    @Test
    void skipsSystemModel() throws Throwable {
        String systemModel = ModelConstant.SYSTEM_MODEL.iterator().next();
        ProceedingJoinPoint pjp = pjp(new Object[]{systemModel});
        try (MockedStatic<ModelManager> mm = mockStatic(ModelManager.class)) {
            assertEquals(LOCAL, aspect.around(pjp));
            mm.verify(() -> ModelManager.getModel(anyString()), never());
        }
    }

    @Test
    void proceedsWhenFirstArgIsNotAModelName() throws Throwable {
        ProceedingJoinPoint pjp = pjp(new Object[]{1L});
        assertEquals(LOCAL, aspect.around(pjp));
        verify(apiClient, never()).modelRPC(anyString(), anyString(), anyString(), any());
    }
}
