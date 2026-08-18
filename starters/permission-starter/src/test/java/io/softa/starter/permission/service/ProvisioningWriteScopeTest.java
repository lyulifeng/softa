package io.softa.starter.permission.service;

import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.MockedStatic;
import org.mockito.Mockito;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.PermissionException;
import io.softa.framework.orm.enums.AccessType;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.ModelService;
import io.softa.starter.permission.scope.ScopeApplicabilityResolver;
import io.softa.starter.permission.spi.PermissionInfo;
import io.softa.starter.permission.spi.PermissionSnapshotProvider;
import io.softa.starter.permission.spi.ScopeRule;
import io.softa.starter.permission.spi.ScopeType;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Why user provisioning has to run outside row scope.
 *
 * <p>The scenario is the one that broke: a role granted "create employee" and nothing else. The
 * Employee row goes in fine, and then the same call provisions that employee's login — an account, a
 * person, a credentials row. {@code UserProfile} is anchorless (no departmentId / employeeId of its
 * own) and nothing the role holds references it, so {@code checkIdsAccess} has no way to answer and
 * fails closed. The ids it is asked about were minted moments earlier by this very call, so no rule
 * could ever have put them in scope: the check has no passing state for a non-admin, ever.
 *
 * <p>These two tests are the behavioural half of the fix. {@code ProvisioningScopeWaiverTest} in
 * user-starter pins that the provisioning methods carry {@code @SkipPermissionCheck}; this pins what
 * that annotation buys them, and why omitting it is not a style question. Admins never see either
 * failure — {@code isAdmin} short-circuits before any of this — which is exactly what makes the
 * regression invisible in a developer's own testing.
 */
class ProvisioningWriteScopeTest {

    private static final Long TENANT = 1L;
    private static final Long USER = 42L;
    private static final List<Long> MINTED_IDS = List.of(9001L);

    /** No scope type beyond the universal ones applies → the model has no forward anchor. */
    private static final Set<ScopeType> ANCHORLESS =
            EnumSet.of(ScopeType.ALL, ScopeType.CUSTOM, ScopeType.CREATED_BY_SELF);

    private MockedStatic<ModelManager> modelManager;
    private PermissionServiceImpl service;

    @BeforeEach
    void setUp() {
        modelManager = Mockito.mockStatic(ModelManager.class);

        ScopeApplicabilityResolver applicability = mock(ScopeApplicabilityResolver.class);
        when(applicability.applicableFor("UserProfile")).thenReturn(ANCHORLESS);

        // The role holds one rule, on Employee — precisely what "create employee and nothing else"
        // means, and what the role wizard produces. Nothing on the user models.
        PermissionInfo pi = new PermissionInfo();
        ScopeRule all = new ScopeRule();
        all.setScopeType(ScopeType.ALL);
        pi.setModelScopeMap(Map.of("Employee", List.of(all)));
        PermissionSnapshotProvider provider = mock(PermissionSnapshotProvider.class);
        when(provider.get(anyLong(), anyLong())).thenReturn(pi);

        // Employee declares no relation onto UserProfile: the link runs Employee -> UserAccount ->
        // UserProfile, and findReferencer only scans models the role actually has a rule on, so the
        // second hop is invisible here. This is the real metadata shape, not a contrived one.
        modelManager.when(() -> ModelManager.existModel("Employee")).thenReturn(true);
        modelManager.when(() -> ModelManager.getModelFields("Employee")).thenReturn(List.of());

        @SuppressWarnings("unchecked")
        ModelService<Long> modelService = mock(ModelService.class);
        service = new PermissionServiceImpl(provider, null, null, modelService, applicability);
    }

    @AfterEach
    void tearDown() {
        modelManager.close();
    }

    private void asCaller(Runnable body, boolean skipPermissionCheck) {
        Context ctx = new Context();
        ctx.setTenantId(TENANT);
        ctx.setUserId(USER);
        ctx.setSkipPermissionCheck(skipPermissionCheck);
        ContextHolder.runWith(ctx, body);
    }

    @Test
    @DisplayName("without the waiver, provisioning a person fails for a role that may create employees")
    void anchorlessProvisioningWriteFailsClosed() {
        PermissionException e = assertThrows(PermissionException.class, () ->
                asCaller(() -> service.checkIdsAccess("UserProfile", MINTED_IDS, AccessType.CREATE), false));

        // The message names a model the caller never asked to touch — the symptom that made this
        // hard to place: the request was POST /Employee/createOne.
        assertTrue(e.getMessage().contains("UserProfile"),
                "expected the failure to name UserProfile, got: " + e.getMessage());
    }

    @Test
    @DisplayName("@SkipPermissionCheck's effect: the same write goes through")
    void waivedProvisioningWriteIsAllowed() {
        // skipPermissionCheck=true is exactly what the annotation's aspect sets around
        // registerInvitedUser / registerNewUser / registerUserProfile.
        assertDoesNotThrow(() ->
                asCaller(() -> service.checkIdsAccess("UserProfile", MINTED_IDS, AccessType.CREATE), true));
    }
}
