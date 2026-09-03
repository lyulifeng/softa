package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import io.softa.framework.base.message.MailRequestMessage;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.entity.UserInvitation;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Who a self-service password reset may be issued for.
 *
 * <p>The trace this closes: Ada left, kept ada@personal.com as her login, never set a password, and
 * was re-hired. Her revived row sits PENDING with ada@acme.com — a mailbox the company holds — in
 * its work-email column. Before this, forgotPassword matched that column, mailed /set-password to
 * that mailbox, and acceptToken set Ada's GLOBAL password from it: the company became Ada at every
 * company she belongs to. The mailbox proves the company, not the person, so the reset resolves
 * the PERSON by login identifier and issues only for one who already has a password and an ACTIVE
 * row — while answering every identifier the same way, so nothing here says who is registered.
 */
class ForgotPasswordScopeTest {

    private static final Long ADA = 7L;
    private static final String WORK_EMAIL = "ada@acme.com";
    private static final String PERSONAL_EMAIL = "ada@personal.com";

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UserInvitationServiceImpl service = spy(new UserInvitationServiceImpl(
            accountService, identityService, eventPublisher, null, mock(JoinProofGuard.class),
            "https://app.example.test"));

    ForgotPasswordScopeTest() {
        // The inherited ORM surface: this test is about whether a token is issued, not how it is stored.
        doReturn(List.<UserInvitation>of()).when(service).searchList(any(Filters.class));
        doReturn(1L).when(service).createOne(any(UserInvitation.class));
        // The work column still matches the revived row — that is exactly the lookup that must no
        // longer decide anything.
        when(accountService.getUserByEmail(anyString())).thenAnswer(inv -> Optional.empty());
    }

    private UserIdentity ada(String loginEmail, String password) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(ADA);
        identity.setLoginEmail(loginEmail);
        identity.setPassword(password);
        if (loginEmail != null) {
            when(identityService.findByLoginIdentifier(loginEmail)).thenReturn(Optional.of(identity));
        }
        return identity;
    }

    private UserAccount row(Long id, Long tenantId, AccountStatus status, String workEmail) {
        UserAccount account = new UserAccount();
        account.setId(id);
        account.setTenantId(tenantId);
        account.setProfileId(ADA);
        account.setStatus(status);
        account.setEmail(workEmail);
        return account;
    }

    private void nothingIssued() {
        verify(service, never()).createOne(any(UserInvitation.class));
        verify(eventPublisher, never()).publishEvent(any());
    }

    private UserInvitation issuedInvitation() {
        ArgumentCaptor<UserInvitation> captor = ArgumentCaptor.forClass(UserInvitation.class);
        verify(service).createOne(captor.capture());
        return captor.getValue();
    }

    @Test
    void theRevivedRowsWorkMailbox_issuesNothing() {
        // Ada's PENDING revived row carries the work address; her login is personal and she has no
        // password. The company typing its own mailbox must get exactly what an unknown address gets.
        UserAccount revived = row(100L, 1L, AccountStatus.PENDING, WORK_EMAIL);
        when(accountService.getUserByEmail(WORK_EMAIL)).thenReturn(Optional.of(revived));
        ada(PERSONAL_EMAIL, null);
        when(identityService.findByLoginIdentifier(WORK_EMAIL)).thenReturn(Optional.empty());
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(revived));

        service.forgotPassword(WORK_EMAIL);

        // Load-bearing: no token row, no mail — the work column is never consulted for a reset.
        nothingIssued();
    }

    @Test
    void anActivePersonWithAPassword_getsATokenAgainstAnActiveRow() {
        ada(PERSONAL_EMAIL, "hash");
        UserAccount pendingElsewhere = row(100L, 1L, AccountStatus.PENDING, WORK_EMAIL);
        UserAccount active = row(200L, 2L, AccountStatus.ACTIVE, "ada@other.com");
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(pendingElsewhere, active));

        service.forgotPassword(PERSONAL_EMAIL);

        // The row the token names is what acceptToken reads back; it must be one already confirmed.
        assertThat(issuedInvitation().getUserId()).isEqualTo(200L);
        ArgumentCaptor<Object> mail = ArgumentCaptor.forClass(Object.class);
        verify(eventPublisher).publishEvent(mail.capture());
        assertThat(((MailRequestMessage) mail.getValue()).to()).containsExactly("ada@other.com");
    }

    @Test
    void theActiveRowWhoseWorkEmailWasTyped_isPreferred() {
        // Two ACTIVE companies, the person typed one company's address: the link goes there, not
        // to whichever row the query happened to return first.
        ada(WORK_EMAIL, "hash");
        UserAccount other = row(200L, 2L, AccountStatus.ACTIVE, "ada@other.com");
        UserAccount acme = row(300L, 3L, AccountStatus.ACTIVE, "Ada@Acme.com");
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(other, acme));

        service.forgotPassword(WORK_EMAIL);

        assertThat(issuedInvitation().getUserId()).isEqualTo(300L);
    }

    @Test
    void aPasswordHolderWithOnlyInvitedRows_getsNothing() {
        // The identifier is theirs and a password exists, but no membership has been confirmed:
        // /join's confirm step still guards every row, and a reset link would route around it.
        ada(PERSONAL_EMAIL, "hash");
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(
                row(100L, 1L, AccountStatus.INVITED, WORK_EMAIL),
                row(200L, 2L, AccountStatus.INVITED, "ada@other.com")));

        service.forgotPassword(PERSONAL_EMAIL);

        nothingIssued();
    }

    @Test
    void anIdentityWithNoPassword_getsNothing_evenWithAnActiveRow() {
        // A reset presupposes a credential to replace. The first password is set in-session or on
        // /join behind a code — never from a mailbox alone.
        ada(PERSONAL_EMAIL, null);
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(
                row(200L, 2L, AccountStatus.ACTIVE, "ada@other.com")));

        service.forgotPassword(PERSONAL_EMAIL);

        nothingIssued();
    }

    @Test
    void anUnknownIdentifier_getsNothing_andIsIndistinguishable() {
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());

        service.forgotPassword("nobody@example.test");

        nothingIssued();
        verify(accountService, never()).listMembershipsOf(any());
    }

    @Test
    void theIdentifierIsLookedUpTheWayLoginSpellsIt() {
        // Trimmed and lowercased before the lookup, as login does, so "Ada@Personal.com " resets
        // the same person who signs in as ada@personal.com.
        ada(PERSONAL_EMAIL, "hash");
        when(accountService.listMembershipsOf(ADA)).thenReturn(List.of(
                row(200L, 2L, AccountStatus.ACTIVE, "ada@other.com")));

        service.forgotPassword("  Ada@Personal.com ");

        verify(identityService).findByLoginIdentifier(PERSONAL_EMAIL);
        assertThat(issuedInvitation().getUserId()).isEqualTo(200L);
    }
}
