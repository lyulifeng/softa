package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.service.CacheService;
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
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The password lockout (PRD D5 / A8) — "linked across tenants" comes free from the credential
 * being global, so what has to be tested is the enforcement:
 *
 * <ul>
 *   <li>the lock is checked BEFORE the password, or the "locked" versus "incorrect" split confirms
 *       a correct guess to whoever is making it;</li>
 *   <li>it locks the PASSWORD only — a verification code still gets the person in, otherwise a
 *       guessing attempt against them succeeds as a denial of service;</li>
 *   <li>the lock is persisted while the counter is not, so flushing the cache cannot unlock and
 *       wrong guesses cannot be used to generate write load.</li>
 * </ul>
 */
class PasswordLockoutTest {

    private static final Long PROFILE = 7L;
    private static final Long IDENTITY = 11L;
    private static final Long ACCOUNT = 100L;
    private static final String EMAIL = "alice@acme.com";

    // ── the login side ──────────────────────────────────────────────

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final TenantInfoService tenantInfoService = mock(TenantInfoService.class);
    private final LoginServiceImpl loginService = new LoginServiceImpl();

    // ── the identity side ───────────────────────────────────────────

    private final CacheService cacheService = mock(CacheService.class);
    private final UserIdentityServiceImpl identityImpl =
            spy(new UserIdentityServiceImpl(cacheService));

    PasswordLockoutTest() {
        ReflectionTestUtils.setField(loginService, "accountService", accountService);
        ReflectionTestUtils.setField(loginService, "identityService", identityService);
        ReflectionTestUtils.setField(loginService, "profileService", profileService);
        ReflectionTestUtils.setField(loginService, "tenantInfoService", tenantInfoService);
    }

    private static UserIdentity identity(LocalDateTime lockedUntil) {
        UserIdentity identity = new UserIdentity();
        identity.setId(IDENTITY);
        identity.setProfileId(PROFILE);
        identity.setLoginEmail(EMAIL);
        identity.setPassword("hash");
        identity.setPasswordLockedUntil(lockedUntil);
        return identity;
    }

    private void givenOneCompany() {
        UserAccount account = new UserAccount();
        account.setId(ACCOUNT);
        account.setTenantId(1L);
        account.setProfileId(PROFILE);
        account.setStatus(AccountStatus.ACTIVE);
        when(accountService.listMembershipsOf(PROFILE)).thenReturn(List.of(account));
        when(profileService.getUserInfo(ACCOUNT)).thenReturn(new UserInfo());
    }

    @Test
    void aLockedPerson_isRefusedWithoutTheirPasswordBeingChecked() {
        // The order is the security property: checking the password first would answer "was my
        // guess right?" through the difference between the two messages.
        UserIdentity person = identity(LocalDateTime.now().plusMinutes(5));
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.isPasswordLocked(person)).thenReturn(true);

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "whatever"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("locked");

        verify(identityService, never()).matchesPassword(any(), anyString());
    }

    @Test
    void aLockedPerson_canStillGetInWithAVerificationCode() {
        // What is locked is the password. Locking the person out of every route would finish the
        // attack on their behalf, so the code path must not consult the lock at all.
        UserIdentity person = identity(LocalDateTime.now().plusMinutes(5));
        VerificationCodeGuard codeGuard = mock(VerificationCodeGuard.class);
        ReflectionTestUtils.setField(loginService, "codeGuard", codeGuard);
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.isPasswordLocked(person)).thenReturn(true);
        givenOneCompany();

        AuthenticationResult result = loginService.authenticateByCode(EMAIL, "123456");

        assertThat(result.isResolved()).isTrue();
        verify(codeGuard).verify(EMAIL, "123456");
        verify(identityService, never()).isPasswordLocked(any());
        verify(identityService, never()).recordPasswordFailure(any());
    }

    @Test
    void aWrongPassword_isCounted_andASuccessfulOneClearsTheCount() {
        UserIdentity person = identity(null);
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.isPasswordLocked(person)).thenReturn(false);
        when(identityService.matchesPassword(person, "wrong")).thenReturn(false);
        when(identityService.matchesPassword(person, "right")).thenReturn(true);
        givenOneCompany();

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"));
        verify(identityService).recordPasswordFailure(person);

        loginService.authenticateByPassword(EMAIL, "right");
        verify(identityService).clearPasswordFailures(IDENTITY);
    }

    // ── what the person is told (PRD L3) ────────────────────────────

    private void givenWrongPasswordIsFailureNumber(UserIdentity person, long count) {
        when(identityService.findByLoginIdentifier(EMAIL)).thenReturn(Optional.of(person));
        when(identityService.isPasswordLocked(person)).thenReturn(false);
        when(identityService.matchesPassword(person, "wrong")).thenReturn(false);
        when(identityService.recordPasswordFailure(person)).thenReturn(count);
    }

    @Test
    void earlyFailures_getThePlainRefusal() {
        // No countdown for the first guesses: it would tell an attacker exactly how much room is
        // left, and only the legitimate owner gains from it — once a lock is actually near.
        givenWrongPasswordIsFailureNumber(identity(null), 6);

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Incorrect account or password.");
    }

    @Test
    void fromTheSeventhFailure_theRemainingAttemptsAreNamed() {
        givenWrongPasswordIsFailureNumber(identity(null), 7);

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"))
                .hasMessage("Incorrect account or password. 3 attempt(s) remaining before password login is locked.");
    }

    @Test
    void theNinthFailure_warnsOfTheLastAttempt() {
        givenWrongPasswordIsFailureNumber(identity(null), 9);

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"))
                .hasMessage("Incorrect account or password. 1 attempt(s) remaining before password login is locked.");
    }

    @Test
    void theTenthFailure_isAnsweredWithTheLock() {
        // The guess that locks says so itself, instead of a plain "incorrect" followed by an
        // unexplained "locked" on the next try.
        givenWrongPasswordIsFailureNumber(identity(null), 10);

        assertThatThrownBy(() -> loginService.authenticateByPassword(EMAIL, "wrong"))
                .hasMessageContaining("Too many failed attempts")
                .hasMessageContaining("locked for 30 minutes");
    }

    // ── an unknown identifier must not be an oracle (PRD L3 / #6) ───

    private static final String NOBODY = "nobody@acme.com";

    private void givenUnknownIdentifierIsFailureNumber(long count) {
        when(identityService.findByLoginIdentifier(NOBODY)).thenReturn(Optional.empty());
        when(identityService.recordUnknownIdentifierFailure(NOBODY)).thenReturn(count);
    }

    private String messageFor(String identifier) {
        try {
            loginService.authenticateByPassword(identifier, "wrong");
            return null;
        } catch (BusinessException e) {
            return e.getMessage();
        }
    }

    @Test
    void anUnknownIdentifier_isCounted_andGetsTheSameCountdown() {
        // Before this, the unknown branch threw before counting: seven tries against a real
        // identifier produced a countdown, seven against a made-up one never did — a yes/no on
        // "does this account exist" for anyone patient enough to ask seven times.
        givenUnknownIdentifierIsFailureNumber(7);

        assertThatThrownBy(() -> loginService.authenticateByPassword(NOBODY, "wrong"))
                .isInstanceOf(BusinessException.class)
                .hasMessage("Incorrect account or password. 3 attempt(s) remaining before password login is locked.");

        verify(identityService).recordUnknownIdentifierFailure(NOBODY);
        verify(identityService, never()).recordPasswordFailure(any());
    }

    @Test
    void anUnknownIdentifier_isToldItIsLockedOnTheTenthTry() {
        givenUnknownIdentifierIsFailureNumber(10);

        assertThatThrownBy(() -> loginService.authenticateByPassword(NOBODY, "wrong"))
                .hasMessageContaining("Too many failed attempts")
                .hasMessageContaining("locked for 30 minutes");
    }

    @Test
    void knownAndUnknownIdentifiers_readIdenticallyAtEveryCount() {
        // The security property in one line: for every count in the window, the refusal for a wrong
        // password against a real person equals the refusal for a made-up identifier at the same
        // count. Any difference — wording or the count at which it changes — is the oracle again.
        for (long count = 1; count <= UserIdentityService.FAILURES_BEFORE_LOCK; count++) {
            givenWrongPasswordIsFailureNumber(identity(null), count);
            givenUnknownIdentifierIsFailureNumber(count);

            assertThat(messageFor(NOBODY))
                    .as("failure #%d", count)
                    .isNotNull()
                    .isEqualTo(messageFor(EMAIL));
        }
    }

    @Test
    void theUnknownCounter_neverHoldsTheGuessItself_andIgnoresCase() {
        // The key is a digest of the lowercased identifier: a cache full of raw guesses would be a
        // list of every address anyone tried, and two spellings of one address are one guess.
        when(cacheService.increment(anyString(), anyLong())).thenReturn(7L);

        assertThat(identityImpl.recordUnknownIdentifierFailure("Nobody@Acme.com")).isEqualTo(7L);
        assertThat(identityImpl.recordUnknownIdentifierFailure(NOBODY)).isEqualTo(7L);

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(cacheService, times(2)).increment(keys.capture(), eq(30L * 60));
        assertThat(keys.getAllValues()).hasSize(2);
        assertThat(keys.getAllValues().get(0))
                .isEqualTo(keys.getAllValues().get(1))
                .startsWith("login:pwd-failures:unknown:")
                .doesNotContainIgnoringCase("nobody")
                .doesNotContainIgnoringCase("acme");
        verify(identityImpl, never()).updateOne(any(UserIdentity.class));
    }

    // ── the counter and the lock itself ─────────────────────────────

    @Test
    void belowTheThreshold_nothingIsWritten_andTheCountIsReported() {
        // Wrong guesses must not be a way to generate row writes on demand. The count still comes
        // back, because the login path words its refusal from it.
        when(cacheService.increment(anyString(), anyLong())).thenReturn(3L);

        assertThat(identityImpl.recordPasswordFailure(identity(null))).isEqualTo(3L);

        verify(identityImpl, never()).updateOne(any(UserIdentity.class));
    }

    @Test
    void atTheThreshold_theLockIsPersisted_andTheCounterReset() {
        // Persisted because a cache flush must not be an unlock; the counter is reset with it so
        // one wrong password after expiry does not immediately re-lock.
        UserIdentity person = identity(null);
        when(cacheService.increment(anyString(), anyLong())).thenReturn(10L);
        doReturn(true).when(identityImpl).updateOne(person);

        assertThat(identityImpl.recordPasswordFailure(person)).isEqualTo(10L);

        assertThat(person.getPasswordLockedUntil()).isAfter(LocalDateTime.now().plusMinutes(29));
        verify(identityImpl).updateOne(person);
        verify(cacheService).clear("login:pwd-failures:" + IDENTITY);
    }

    @Test
    void settingAPassword_clearsTheLock_notJustTheCounter() {
        // The reset path's whole point: someone who forgot their password must be able to use the
        // one they just set. Leaving passwordLockedUntil standing would refuse them for 30 more
        // minutes, on a lock the reset itself resolved.
        UserIdentity person = identity(LocalDateTime.now().plusMinutes(29));
        doReturn(Optional.of(person)).when(identityImpl).getById(IDENTITY);
        doReturn(true).when(identityImpl).updateOne(person, false);

        identityImpl.setPassword(IDENTITY, "N3w-Passw0rd");

        assertThat(person.getPasswordLockedUntil()).isNull();
        // The overload that keeps nulls — the default one would drop exactly this write.
        verify(identityImpl).updateOne(person, false);
        verify(cacheService).clear("login:pwd-failures:" + IDENTITY);
    }

    @Test
    void anExpiredLock_isNotALock() {
        assertThat(identityImpl.isPasswordLocked(identity(LocalDateTime.now().minusMinutes(1))))
                .isFalse();
        assertThat(identityImpl.isPasswordLocked(identity(LocalDateTime.now().plusMinutes(1))))
                .isTrue();
        assertThat(identityImpl.isPasswordLocked(identity(null))).isFalse();
    }
}
