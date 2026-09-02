package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.starter.user.dto.JoinVerification;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserInvitationService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * /join on a membership that already belongs to a person — the re-hired leaver.
 *
 * <p>Re-hire revives the closed row with its profileId intact and HR sends a fresh invitation to
 * the work address on it. That address was released from the person's identity at off-boarding,
 * so the flow's find-or-create by address sees nobody. Left to itself it would mint a second person
 * and confirmJoin would bind the row to it: the real person's password and other-company
 * memberships stay on a profileId that no membership points at any more, and a duplicate exists.
 * The row already knows whose it is, and that answer has to win over the address.
 */
class RevivedJoinTest {

    private static final String TOKEN = "raw-token";
    private static final Long ADA = 7L;
    private static final String RELEASED_WORK_EMAIL = "ada@acme.com";

    private final UserInvitationService invitationService = mock(UserInvitationService.class);
    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final VerificationCodeGuard codeGuard = mock(VerificationCodeGuard.class);
    private final LoginServiceImpl loginService = new LoginServiceImpl();

    RevivedJoinTest() {
        ReflectionTestUtils.setField(loginService, "invitationService", invitationService);
        ReflectionTestUtils.setField(loginService, "accountService", accountService);
        ReflectionTestUtils.setField(loginService, "identityService", identityService);
        ReflectionTestUtils.setField(loginService, "profileService", profileService);
        ReflectionTestUtils.setField(loginService, "codeGuard", codeGuard);
        when(invitationService.resolveJoinChannel(TOKEN, "email")).thenReturn(RELEASED_WORK_EMAIL);
        when(accountService.isWorkContactShared(RELEASED_WORK_EMAIL)).thenReturn(false);
        // Nobody's login identifier any more: off-boarding released it on purpose.
        when(identityService.findByLoginIdentifier(RELEASED_WORK_EMAIL)).thenReturn(Optional.empty());
    }

    private UserAccount invitationIsFor(Long profileId) {
        UserAccount account = new UserAccount();
        account.setId(100L);
        account.setProfileId(profileId);
        account.setEmail(RELEASED_WORK_EMAIL);
        when(invitationService.resolveJoinAccount(TOKEN)).thenReturn(Optional.of(account));
        return account;
    }

    private static UserIdentity identityOf(Long profileId, String loginEmail, String loginMobile) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(profileId);
        identity.setLoginEmail(loginEmail);
        identity.setLoginMobile(loginMobile);
        identity.setPassword("hash");
        return identity;
    }

    @Test
    void aRevivedMembership_resolvesToItsOwnPerson_andGetsTheReleasedIdentifierBack() {
        invitationIsFor(ADA);
        UserIdentity ada = identityOf(ADA, null, "+6591234567");
        when(identityService.findByProfile(ADA)).thenReturn(Optional.of(ada));
        when(identityService.isIdentifierClaimable(RELEASED_WORK_EMAIL, ADA)).thenReturn(true);

        JoinVerification result = loginService.verifyJoinCode(TOKEN, "email", "123456");

        assertThat(result.profileId()).isEqualTo(ADA);
        assertThat(result.mustSetPassword()).isFalse();   // she still has her password
        // No second Ada.
        verify(profileService, never()).createPersonForJoin(anyString());
        // The work address comes home as her login identifier: she proved control of it by code,
        // and she is the person who lost it at off-boarding.
        assertThat(ada.getLoginEmail()).isEqualTo(RELEASED_WORK_EMAIL);
        verify(identityService).updateOne(ada);
    }

    @Test
    void anAddressSomeoneElseClaimedMeanwhile_isNotReboundOntoThePerson() {
        // The address was reissued and another identity holds it now. Binding it to Ada as well
        // would make the identifier ambiguous for both; her person still resolves from the row.
        invitationIsFor(ADA);
        UserIdentity ada = identityOf(ADA, null, null);
        when(identityService.findByProfile(ADA)).thenReturn(Optional.of(ada));
        when(identityService.isIdentifierClaimable(RELEASED_WORK_EMAIL, ADA)).thenReturn(false);

        assertThat(loginService.verifyJoinCode(TOKEN, "email", "123456").profileId()).isEqualTo(ADA);

        assertThat(ada.getLoginEmail()).isNull();
        verify(identityService, never()).updateOne(any(UserIdentity.class));
    }

    @Test
    void aPersonalLoginEmail_isNotOverwrittenByTheWorkOne() {
        invitationIsFor(ADA);
        UserIdentity ada = identityOf(ADA, "ada@personal.com", null);
        when(identityService.findByProfile(ADA)).thenReturn(Optional.of(ada));

        loginService.verifyJoinCode(TOKEN, "email", "123456");

        assertThat(ada.getLoginEmail()).isEqualTo("ada@personal.com");
        verify(identityService, never()).isIdentifierClaimable(anyString(), any());
        verify(identityService, never()).updateOne(any(UserIdentity.class));
    }

    @Test
    void anUnboundMembership_stillFindsOrCreatesByAddress() {
        // The ordinary first-time invitee: the row belongs to nobody, so the address decides.
        invitationIsFor(null);
        when(profileService.createPersonForJoin(RELEASED_WORK_EMAIL)).thenReturn(42L);

        JoinVerification result = loginService.verifyJoinCode(TOKEN, "email", "123456");

        assertThat(result.profileId()).isEqualTo(42L);
        assertThat(result.mustSetPassword()).isTrue();
        verify(identityService).findByLoginIdentifier(RELEASED_WORK_EMAIL);
        verify(identityService, never()).findByProfile(any());
    }
}
