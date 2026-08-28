package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.entity.UserInvitation;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.enums.InvitationStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The authorization tie on {@code confirmJoin} (PRD §4.4).
 *
 * <p>The endpoint is anonymous — whoever holds the link reaches it with no session — and it takes
 * {@code profileId} from the caller. Without a check that the named person is the one the
 * invitation was addressed to, a holder of ANY valid invitation could bind their account to
 * someone else's person by naming that person's id. {@code setJoinPassword} already makes this tie;
 * this asserts {@code confirmJoin} makes the same one, because it is the call that actually writes
 * the binding.
 */
class ConfirmJoinAuthorizationTest {

    private static final String TOKEN = "raw-token";
    private static final Long ACCOUNT_ID = 100L;
    private static final Long TENANT = 1L;
    private static final Long RIGHTFUL_PROFILE = 7L;
    private static final Long ATTACKER_TARGET_PROFILE = 999L;

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UserInvitationServiceImpl service = spy(new UserInvitationServiceImpl(
            accountService, identityService, eventPublisher, null, "http://localhost:3000"));

    private UserInvitation givenPendingInvitationTo(String email, String mobile) {
        UserInvitation invitation = new UserInvitation();
        invitation.setId(55L);
        invitation.setUserId(ACCOUNT_ID);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setEmail(email);
        invitation.setMobile(mobile);
        invitation.setExpiresAt(LocalDateTime.now().plusDays(1));
        doReturn(Optional.of(invitation)).when(service).searchOne(any(Filters.class));

        UserAccount account = new UserAccount();
        account.setId(ACCOUNT_ID);
        account.setTenantId(TENANT);
        account.setStatus(AccountStatus.INVITED);
        account.setEmail(email);
        account.setMobile(mobile);
        when(accountService.getById(ACCOUNT_ID)).thenReturn(Optional.of(account));
        when(accountService.listMembershipsOf(any())).thenReturn(List.of());
        doReturn(true).when(service).updateOne(any(UserInvitation.class));
        return invitation;
    }

    private static UserIdentity identity(String loginEmail, String loginMobile) {
        UserIdentity identity = new UserIdentity();
        identity.setLoginEmail(loginEmail);
        identity.setLoginMobile(loginMobile);
        return identity;
    }

    @Test
    void theRightfulInvitee_canConfirm() {
        givenPendingInvitationTo("alice@acme.com", null);
        when(identityService.findByProfile(RIGHTFUL_PROFILE))
                .thenReturn(Optional.of(identity("alice@acme.com", null)));

        service.confirmJoin(TOKEN, RIGHTFUL_PROFILE);

        verify(accountService).updateOne(any(UserAccount.class));
    }

    @Test
    void aLinkHolder_cannotBindTheirAccountToSomeoneElsesPerson() {
        // The attack: a valid invitation for alice@acme.com, confirmed while naming a stranger's
        // profileId whose login identifier this invitation was never addressed to.
        givenPendingInvitationTo("alice@acme.com", null);
        when(identityService.findByProfile(ATTACKER_TARGET_PROFILE))
                .thenReturn(Optional.of(identity("victim@elsewhere.com", "+8613800138000")));

        assertThatThrownBy(() -> service.confirmJoin(TOKEN, ATTACKER_TARGET_PROFILE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("does not belong");

        verify(accountService, never()).updateOne(any(UserAccount.class));
    }

    @Test
    void aReHireViaNewInvitation_isToldToUseReHire_notAConstraintError() {
        // The person left this company before: their DEACTIVATED row still holds the (tenant,
        // profile) slot. Binding here would hit the unique index; listMembershipsOf hides that row,
        // so without this the operator sees a raw constraint error instead of "use re-hire".
        givenPendingInvitationTo("alice@acme.com", null);
        when(identityService.findByProfile(RIGHTFUL_PROFILE))
                .thenReturn(Optional.of(identity("alice@acme.com", null)));
        UserAccount closed = new UserAccount();
        closed.setId(77L);
        closed.setTenantId(TENANT);
        closed.setStatus(AccountStatus.DEACTIVATED);
        when(accountService.findMembershipInTenant(TENANT, RIGHTFUL_PROFILE))
                .thenReturn(Optional.of(closed));

        assertThatThrownBy(() -> service.confirmJoin(TOKEN, RIGHTFUL_PROFILE))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("re-hire");
        verify(accountService, never()).updateOne(any(UserAccount.class));
    }
}
