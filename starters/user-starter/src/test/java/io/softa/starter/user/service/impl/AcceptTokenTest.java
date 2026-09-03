package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserInvitation;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.enums.InvitationPurpose;
import io.softa.starter.user.enums.InvitationStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The emailed-link token sets a PASSWORD and nothing else.
 *
 * <p>Activation belongs to /join's confirm step, where the person agrees to join the company. If
 * accepting a token also activated a not-yet-active membership, a reset link would become a way
 * to skip that step — and the account list would show "active" for someone who never confirmed.
 */
class AcceptTokenTest {

    private static final Long ACCOUNT = 100L;

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserInvitationServiceImpl invitationService = spy(new UserInvitationServiceImpl(
            accountService, identityService, mock(ApplicationEventPublisher.class), null,
            mock(JoinProofGuard.class), "http://localhost:3000"));

    private UserAccount tokenFor(AccountStatus status) {
        UserAccount account = new UserAccount();
        account.setId(ACCOUNT);
        account.setStatus(status);
        when(accountService.getById(ACCOUNT)).thenReturn(Optional.of(account));
        UserInvitation invitation = new UserInvitation();
        invitation.setId(1L);
        invitation.setUserId(ACCOUNT);
        invitation.setPurpose(InvitationPurpose.PASSWORD_RESET);
        invitation.setStatus(InvitationStatus.PENDING);
        invitation.setExpiresAt(LocalDateTime.now().plusHours(1));
        doReturn(Optional.of(invitation)).when(invitationService).searchOne(any(Filters.class));
        doReturn(true).when(invitationService).updateOne(any(UserInvitation.class));
        return account;
    }

    @Test
    void acceptingATokenLeavesTheAccountStatusAlone() {
        UserAccount account = tokenFor(AccountStatus.INVITED);

        invitationService.acceptToken("raw-token", "Str0ng!Passw0rd");

        assertThat(account.getStatus()).isEqualTo(AccountStatus.INVITED);
        assertThat(account.getActivationTime()).isNull();
        verify(accountService, never()).updateOne(any(UserAccount.class));
    }

    @Test
    void acceptingATokenSetsThePasswordAndClosesTheInvitation() {
        tokenFor(AccountStatus.ACTIVE);

        invitationService.acceptToken("raw-token", "Str0ng!Passw0rd");

        verify(identityService).setPassword(any(UserAccount.class), org.mockito.ArgumentMatchers.eq("Str0ng!Passw0rd"));
        verify(invitationService).updateOne(org.mockito.ArgumentMatchers
                .argThat((UserInvitation i) -> i.getStatus() == InvitationStatus.ACCEPTED && i.getAcceptedAt() != null));
    }
}
