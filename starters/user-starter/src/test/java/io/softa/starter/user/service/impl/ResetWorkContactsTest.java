package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailRequestMessage;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reset User (W9) — the everyday case: the RIGHT person holds this membership and their email or
 * phone simply changed. Three things make it correct rather than a smaller unbind:
 *
 * <ul>
 *   <li>the LOGIN identifier moves with the contact — leaving it behind means the person signs in
 *       with an address this company no longer knows, while the recycled one becomes a route into
 *       their account for whoever receives it next;</li>
 *   <li>only the value this company ISSUED is rewritten — a personal login email is not ours;</li>
 *   <li>the OLD address is notified, so a reset the person did not ask for reaches somewhere they
 *       can still read.</li>
 * </ul>
 */
class ResetWorkContactsTest {

    private static final Long ACCOUNT = 100L;
    private static final Long PROFILE = 7L;

    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final UserAccountServiceImpl accountService = spy(new UserAccountServiceImpl());

    ResetWorkContactsTest() {
        ReflectionTestUtils.setField(accountService, "identityService", identityService);
        ReflectionTestUtils.setField(accountService, "eventPublisher", eventPublisher);
    }

    private UserAccount given(String email, String mobile, Long profileId) {
        UserAccount account = new UserAccount();
        account.setId(ACCOUNT);
        account.setProfileId(profileId);
        account.setStatus(AccountStatus.ACTIVE);
        account.setEmail(email);
        account.setMobile(mobile);
        doReturn(Optional.of(account)).when(accountService).getById(ACCOUNT);
        doReturn(Optional.empty()).when(accountService).getUserByEmail(any());
        doReturn(true).when(accountService).updateOne(any(UserAccount.class), any(Boolean.class));
        return account;
    }

    private static UserIdentity identity(String loginEmail, String loginMobile) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(PROFILE);
        identity.setLoginEmail(loginEmail);
        identity.setLoginMobile(loginMobile);
        identity.setPassword("hash");
        return identity;
    }

    @Test
    void theLoginIdentifierMovesWithTheWorkContact() {
        UserAccount account = given("old@acme.com", null, PROFILE);
        UserIdentity person = identity("old@acme.com", null);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(person));

        accountService.resetWorkContacts(ACCOUNT, "new@acme.com", null, "Address changed");

        assertThat(account.getEmail()).isEqualTo("new@acme.com");
        assertThat(person.getLoginEmail()).isEqualTo("new@acme.com");
        // The overload that keeps nulls: clearing one channel means writing a null, and the default
        // one would drop it, leaving the old identifier a live login route.
        verify(identityService).updateOne(person, false);
        // The password is untouched — this is not a re-invitation.
        assertThat(person.getPassword()).isEqualTo("hash");
        assertThat(account.getProfileId()).isEqualTo(PROFILE);
    }

    @Test
    void aPersonalLoginEmailIsLeftAlone() {
        // The work email and the login email need not be the same value. Only the address this
        // company issued may be rewritten.
        given("old@acme.com", null, PROFILE);
        UserIdentity person = identity("alice.personal@gmail.com", null);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(person));

        accountService.resetWorkContacts(ACCOUNT, "new@acme.com", null, "Address changed");

        assertThat(person.getLoginEmail()).isEqualTo("alice.personal@gmail.com");
        verify(identityService, never()).updateOne(any(UserIdentity.class), any(Boolean.class));
    }

    @Test
    void theOldAddressIsNotified_notTheNewOne() {
        // If the reset was not the person's own doing, the message must reach where they can still
        // read it. Telling only the new address informs whoever now holds it.
        given("old@acme.com", null, PROFILE);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(identity("old@acme.com", null)));

        accountService.resetWorkContacts(ACCOUNT, "new@acme.com", null, "Address changed");

        var captor = forClass(MailRequestMessage.class);
        verify(eventPublisher).publishEvent(captor.capture());
        assertThat(captor.getValue().to()).containsExactly("old@acme.com");
    }

    @Test
    void anAccountNobodyHoldsYet_isRefused() {
        // Nothing to reset: the membership has no person, so this is an invitation, not a reset.
        given("old@acme.com", null, null);

        assertThatThrownBy(() ->
                accountService.resetWorkContacts(ACCOUNT, "new@acme.com", null, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("invite it instead");
    }

    @Test
    void clearingBothChannels_isRefused() {
        given("old@acme.com", "+8613800138000", PROFILE);

        assertThatThrownBy(() -> accountService.resetWorkContacts(ACCOUNT, " ", null, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("needs a work email or a work mobile");
    }

    @Test
    void anAddressAnotherAccountHolds_isRefused() {
        given("old@acme.com", null, PROFILE);
        UserAccount other = new UserAccount();
        other.setId(999L);
        doReturn(Optional.of(other)).when(accountService).getUserByEmail("taken@acme.com");

        assertThatThrownBy(() ->
                accountService.resetWorkContacts(ACCOUNT, "taken@acme.com", null, "x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already belongs to another account");
        verify(identityService, never()).updateOne(any(UserIdentity.class), any(Boolean.class));
    }

    @Test
    void mailIsAddressedToTheOldChannelOnly_whenThereWasNoOldEmail() {
        // Mobile-only account: no old email means no notification channel here. Silently skipping
        // is right — publishing to an empty receiver list would be a message to nobody, logged as
        // if it went out.
        given(null, "+8613800138000", PROFILE);
        when(identityService.findByProfile(PROFILE))
                .thenReturn(Optional.of(identity(null, "+8613800138000")));

        accountService.resetWorkContacts(ACCOUNT, null, "+8613800138001", "Number changed");

        verify(eventPublisher, never()).publishEvent(any(MailRequestMessage.class));
    }
}
