package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.starter.user.dto.AuthenticationResult;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Login now resolves the PERSON by a login identifier rather than the company account by its work
 * email. Two properties of that switch are load-bearing:
 *
 * <ul>
 *   <li><b>nobody is locked out</b> — identity rows created before identifier seeding existed have
 *       no identifier at all, so resolution falls back to the account's work contact and writes the
 *       identifier back. Without the fallback, this release would simply refuse those people;</li>
 *   <li><b>no account-existence oracle</b> — "no such identifier", "identifier not linked to a
 *       person" and "wrong password" must be indistinguishable from outside, or an anonymous
 *       endpoint tells a stranger which addresses are registered.</li>
 * </ul>
 */
class IdentifierResolutionTest {

    private static final Long PROFILE = 7L;
    private static final Long ACCOUNT = 100L;
    private static final String EMAIL = "alice@acme.com";
    private static final String PASSWORD = "S0me-Passw0rd";
    private static final String FAILED_LOGIN = "Incorrect account or password.";

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final TenantInfoService tenantInfoService = mock(TenantInfoService.class);
    private final LoginServiceImpl loginService = new LoginServiceImpl();

    IdentifierResolutionTest() {
        ReflectionTestUtils.setField(loginService, "accountService", accountService);
        ReflectionTestUtils.setField(loginService, "identityService", identityService);
        ReflectionTestUtils.setField(loginService, "profileService", profileService);
        ReflectionTestUtils.setField(loginService, "tenantInfoService", tenantInfoService);
    }

    private static UserIdentity identity(String loginEmail, String password) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(PROFILE);
        identity.setLoginEmail(loginEmail);
        identity.setPassword(password);
        return identity;
    }

    private static UserAccount membership() {
        UserAccount account = new UserAccount();
        account.setId(ACCOUNT);
        account.setTenantId(1L);
        account.setProfileId(PROFILE);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmail(EMAIL);
        return account;
    }

    /** One enterable company, so authentication resolves straight to a session payload. */
    private void givenOneCompany() {
        when(accountService.listMembershipsOf(PROFILE)).thenReturn(List.of(membership()));
        when(profileService.getUserInfo(ACCOUNT)).thenReturn(new UserInfo());
    }

    @Test
    void identifierOnFile_resolvesDirectly_withoutTouchingTheAccount() {
        UserIdentity person = identity(EMAIL, "hash");
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.matchesPassword(person, PASSWORD)).thenReturn(true);
        givenOneCompany();

        AuthenticationResult result = loginService.authenticateByPassword(EMAIL, PASSWORD);

        assertThat(result.isResolved()).isTrue();
        assertThat(result.profileId()).isEqualTo(PROFILE);
        // Nothing to heal, so nothing is written.
        verify(identityService, never()).adoptIdentifier(any(), anyString());
    }

    @Test
    void noIdentifierYet_fallsBackToTheWorkContact_andBackfillsIt() {
        // The people this exists for: their identity row predates identifier seeding. Resolving by
        // identifier alone would refuse them, on data they never had a chance to supply.
        UserIdentity person = identity(null, "hash");
        UserAccount account = membership();
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.empty());
        when(accountService.getUserByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(identityService.requireIdentity(account)).thenReturn(person);
        when(identityService.matchesPassword(person, PASSWORD)).thenReturn(true);
        givenOneCompany();

        AuthenticationResult result = loginService.authenticateByPassword(EMAIL, PASSWORD);

        assertThat(result.isResolved()).isTrue();
        // Written back, so the next sign-in resolves directly and the data converges on its own.
        verify(identityService).adoptIdentifier(person, EMAIL);
    }

    @Test
    void accountWithNoLinkedPerson_reportsAnOrdinaryFailedLogin() {
        // A data fault inside; from outside it must look exactly like a wrong password, or the
        // distinct message confirms the address is registered.
        UserAccount account = membership();
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.empty());
        when(accountService.getUserByEmail(EMAIL)).thenReturn(Optional.of(account));
        when(identityService.requireIdentity(account))
                .thenThrow(new BusinessException("This account is not linked to a person yet."));

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, PASSWORD))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining(FAILED_LOGIN);
    }

    @Test
    void unknownIdentifier_andWrongPassword_areIndistinguishable() {
        when(identityService.findByLoginIdentifier("nobody@acme.com")).thenReturn(Optional.empty());
        when(accountService.getUserByEmail("nobody@acme.com")).thenReturn(Optional.empty());
        when(accountService.getUserByMobile("nobody@acme.com")).thenReturn(Optional.empty());

        UserIdentity person = identity(EMAIL, "hash");
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.matchesPassword(person, "wrong")).thenReturn(false);

        assertThatThrownBy(() -> loginService.authenticateByPassword("nobody@acme.com", PASSWORD))
                .hasMessageContaining(FAILED_LOGIN);
        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"))
                .hasMessageContaining(FAILED_LOGIN);
    }

    @Test
    void passwordLessPerson_isToldToSetOne_ratherThanBeingLetInSilently() {
        UserIdentity person = identity(EMAIL, null);
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(person));
        when(identityService.matchesPassword(person, PASSWORD)).thenReturn(true);
        givenOneCompany();

        assertThat(loginService.authenticateByPassword(EMAIL, PASSWORD).mustSetPassword()).isTrue();
        assertThat(loginService.mustSetPassword(PROFILE)).isTrue();
    }
}
