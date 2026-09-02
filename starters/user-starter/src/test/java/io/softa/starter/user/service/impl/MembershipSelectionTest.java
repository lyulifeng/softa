package io.softa.starter.user.service.impl;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.starter.user.dto.MembershipOption;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.exception.MultipleMembershipsException;
import io.softa.starter.user.dto.AuthenticationResult;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Authentication says WHO, membership selection says WHERE. This covers the second step —
 * the one that only exists because a person can now belong to several companies.
 *
 * <p>The load-bearing test is {@link #namingSomeoneElsesMembership_isRefused()}: without that
 * ownership check, anyone who authenticated as themselves could name any accountId and be issued
 * a session in a company they are not a member of.
 */
class MembershipSelectionTest {

    private static final Long PROFILE = 7L;
    private static final String TOKEN = "pre-auth-token";

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final TenantInfoService tenantInfoService = mock(TenantInfoService.class);
    private final CacheService cacheService = mock(CacheService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final LoginServiceImpl loginService = new LoginServiceImpl();

    MembershipSelectionTest() {
        ReflectionTestUtils.setField(loginService, "accountService", accountService);
        ReflectionTestUtils.setField(loginService, "tenantInfoService", tenantInfoService);
        ReflectionTestUtils.setField(loginService, "cacheService", cacheService);
        ReflectionTestUtils.setField(loginService, "profileService", profileService);
        ReflectionTestUtils.setField(loginService, "identityService", identityService);
        when(tenantInfoService.getTenantName(1L)).thenReturn("Acme");
        when(tenantInfoService.getTenantName(2L)).thenReturn("Globex");
        // A live pre-auth token standing for PROFILE — what authentication would have minted.
        when(cacheService.get("login:preauth:" + TOKEN)).thenReturn(PROFILE.toString());
    }

    private static UserAccount membership(Long accountId, Long tenantId, AccountStatus status) {
        UserAccount account = new UserAccount();
        account.setId(accountId);
        account.setTenantId(tenantId);
        account.setProfileId(PROFILE);
        account.setStatus(status);
        return account;
    }

    private void givenMemberships(UserAccount... accounts) {
        when(accountService.listMembershipsOf(PROFILE)).thenReturn(List.of(accounts));
    }

    // ── resolveSingleMembership ─────────────────────────────────────────

    @Test
    void oneActiveCompany_resolvesWithoutAsking() {
        // Today's behaviour, unchanged: a single-company person sees no extra step.
        givenMemberships(membership(100L, 1L, AccountStatus.ACTIVE));

        assertThat(loginService.resolveSingleMembership(PROFILE)).isEqualTo(100L);
    }

    @Test
    void twoCompanies_refuseAndCarryTheOptions() {
        givenMemberships(membership(100L, 1L, AccountStatus.ACTIVE),
                membership(200L, 2L, AccountStatus.ACTIVE));

        // Auto-picking would drop someone into a workspace they did not ask for, and the wrong
        // one is worse than an extra click.
        assertThatThrownBy(() -> loginService.resolveSingleMembership(PROFILE))
                .isInstanceOf(MultipleMembershipsException.class)
                .satisfies(e -> assertThat(((MultipleMembershipsException) e).getOptions()).hasSize(2));
    }

    @Test
    void noCompany_refusesWithSomethingActionable() {
        givenMemberships();

        assertThatThrownBy(() -> loginService.resolveSingleMembership(PROFILE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not linked to any company");
    }

    @Test
    void oneFrozenCompany_stillGoesToThePicker() {
        // Not auto-entered (it cannot be entered) and not silently empty either — the person
        // needs to SEE that the company exists and that it is frozen.
        givenMemberships(membership(100L, 1L, AccountStatus.FROZEN));

        assertThatThrownBy(() -> loginService.resolveSingleMembership(PROFILE))
                .isInstanceOf(MultipleMembershipsException.class);
    }

    // ── listCompanies ───────────────────────────────────────────────────

    @Test
    void selectableCompaniesComeFirst() {
        givenMemberships(membership(100L, 1L, AccountStatus.FROZEN),
                membership(200L, 2L, AccountStatus.ACTIVE));

        List<MembershipOption> options = loginService.listCompanies(TOKEN);

        assertThat(options).extracting(MembershipOption::accountId).containsExactly(200L, 100L);
        assertThat(options.get(0).tenantName()).isEqualTo("Globex");
    }

    @Test
    void offBoardedMembershipsNeverAppear() {
        // Excluded by the service query, so the picker cannot show a former employer. Asserted
        // here as the contract listCompanies relies on.
        when(accountService.listMembershipsOf(PROFILE)).thenReturn(List.of());

        assertThat(loginService.listCompanies(TOKEN)).isEmpty();
    }

    // ── selectCompany:所有权校验 ────────────────────────────────────────

    @Test
    void selectingOwnActiveMembership_isAllowed() {
        givenMemberships(membership(100L, 1L, AccountStatus.ACTIVE));
        when(profileService.getUserInfo(100L)).thenReturn(new io.softa.framework.base.context.UserInfo());

        AuthenticationResult result = loginService.selectCompany(TOKEN, 100L);

        assertThat(result.isResolved()).isTrue();
        assertThat(result.profileId()).isEqualTo(PROFILE);
        // Single use: the token is consumed so a leaked one cannot be replayed into a session.
        verify(cacheService).clear("login:preauth:" + TOKEN);
    }

    @Test
    void aFailedSelectionLeavesTheTokenUsable() {
        // The token used to be consumed BEFORE the session was built, so any failure past that
        // point burned it: the page said "could not enter that company, please try again" and the
        // retry answered "your sign-in step expired" — the advice was impossible to follow and the
        // person had to restart the whole login. Nothing is minted on this path, so nothing is
        // replayable.
        givenMemberships(membership(100L, 1L, AccountStatus.ACTIVE));
        when(profileService.getUserInfo(100L)).thenThrow(new BusinessException("boom"));

        assertThatThrownBy(() -> loginService.selectCompany(TOKEN, 100L))
                .isInstanceOf(BusinessException.class);

        verify(cacheService, never()).clear("login:preauth:" + TOKEN);
    }

    @Test
    void namingSomeoneElsesMembership_isRefused() {
        // The security point of the whole step. 999 belongs to another person, so it is simply
        // not in this profile's list — and the message must not confirm that it exists.
        givenMemberships(membership(100L, 1L, AccountStatus.ACTIVE));

        assertThatThrownBy(() -> loginService.selectCompany(TOKEN, 999L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not available for your account");
    }

    @Test
    void anExpiredOrForgedToken_cannotSelectAnyCompany() {
        // The authentication-bypass regression: without a live token, naming a person's accountId
        // must not mint their session. An unknown token resolves to nobody.
        when(cacheService.get("login:preauth:forged")).thenReturn(null);

        assertThatThrownBy(() -> loginService.selectCompany("forged", 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("expired");
        assertThatThrownBy(() -> loginService.listCompanies("forged"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    void selectingAFrozenMembership_isRefusedWithTheReason() {
        givenMemberships(membership(100L, 1L, AccountStatus.FROZEN));

        assertThatThrownBy(() -> loginService.selectCompany(TOKEN, 100L))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("deactivated");
    }
}
