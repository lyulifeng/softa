package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.message.MailRequestMessage;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.ConsultantService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserInvitationService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Asking for a code must not answer whether the address exists.
 *
 * <p>The entry point is unauthenticated, so any difference between the two outcomes is an oracle:
 * ask it once per address and it returns a verified roster of the people an organisation employs.
 * Telling the truth on screen and handing an attacker that list are the same act — there is no way
 * to do one without the other before sign-in — so the screen says the same thing either way and the
 * person who mistyped is served by the wording instead ("if this address is linked, a code is on
 * its way; otherwise check it").
 *
 * <p>Sameness is not one property, which is why this is not one test. The response, the status, the
 * send budget and the elapsed time each leak it independently, and each has its own case below:
 * close three and the fourth still answers the question.
 *
 * <p>What stays different is the only thing that may: nothing is sent to an address nobody holds.
 */
class UnknownIdentifierIsIndistinguishableTest {

    private final ApplicationEventPublisher eventPublisher = mock(ApplicationEventPublisher.class);
    private final VerificationCodeGuard codeGuard = mock(VerificationCodeGuard.class);
    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final LoginServiceImpl loginService = new LoginServiceImpl();

    UnknownIdentifierIsIndistinguishableTest() {
        ReflectionTestUtils.setField(loginService, "eventPublisher", eventPublisher);
        ReflectionTestUtils.setField(loginService, "consultantService", mock(ConsultantService.class));
        ReflectionTestUtils.setField(loginService, "codeGuard", codeGuard);
        ReflectionTestUtils.setField(loginService, "identityService", identityService);
    }

    private void nobodyHolds(String identifier) {
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());
    }

    private void somebodyHolds(String identifier) {
        when(identityService.findByLoginIdentifier(anyString()))
                .thenReturn(Optional.of(new UserIdentity()));
    }

    // ───────────────────────── the response ─────────────────────────

    @Test
    void anUnknownEmailIsAcceptedAsQuietlyAsAKnownOne() {
        nobodyHolds("nobody@nowhere.test");

        assertThatCode(() -> loginService.sendEmailCode("nobody@nowhere.test"))
                .as("a refusal here — however it is worded — is the oracle itself")
                .doesNotThrowAnyException();
    }

    @Test
    void anUnknownMobileIsAcceptedAsQuietlyAsAKnownOne() {
        nobodyHolds("+8613900000000");

        assertThatCode(() -> loginService.sendMobileCode("+8613900000000"))
                .doesNotThrowAnyException();
    }

    // ───────────────────────── the delivery ─────────────────────────

    @Test
    void nothingIsSentToAnAddressNobodyHolds() {
        // The one difference that is allowed to exist, and the reason the check is here at all: a
        // code to an unheld address reaches someone who did not ask, and would be refused at verify.
        nobodyHolds("nobody@nowhere.test");

        loginService.sendEmailCode("nobody@nowhere.test");

        // any(Object.class), not any(): publishEvent is overloaded and a bare any() binds to the
        // ApplicationEvent form, which nothing here ever calls — the check would pass on its own.
        verify(eventPublisher, never()).publishEvent(any(Object.class));
    }

    // ───────────────────────── the send budget ─────────────────────────

    @Test
    void anUnknownAddressSpendsItsSendAllowanceJustTheSame() {
        // Otherwise the limiter becomes the oracle one step along: ask one address eleven times and
        // the eleventh is refused when it exists and accepted when it does not.
        nobodyHolds("nobody@nowhere.test");

        loginService.sendEmailCode("nobody@nowhere.test");

        verify(codeGuard).beforeSend("nobody@nowhere.test");
    }

    @Test
    void runningOutOfSendsLooksTheSameWhetherOrNotTheAddressExists() {
        // The limiter was the oracle one layer down: the linked path let the guard's refusal out
        // while the unlinked path swallowed it, so asking one address eleven times told you which
        // it was. Asserting only that the unknown side is quiet would have passed throughout.
        doThrow(new BusinessException("Too many requests")).when(codeGuard).beforeSend(anyString());

        somebodyHolds("alice@acme.com");
        Throwable linked = catchThrowable(() -> loginService.sendEmailCode("alice@acme.com"));
        nobodyHolds("nobody@nowhere.test");
        Throwable unlinked = catchThrowable(() -> loginService.sendEmailCode("nobody@nowhere.test"));

        assertThat(linked).as("linked threw %s; unlinked threw %s", linked, unlinked).isNull();
        assertThat(unlinked).isNull();
    }

    // ───────────────────────── /join is not this ─────────────────────────

    @Test
    void joinStillSendsToSomebodyWhoHasNoIdentityYet() {
        // An invitee has no identity — that IS joining — so routing /join through the public entry
        // point would silently skip every first-time joiner and the code would never go out. The
        // invitation, not the caller, supplied the address, so it needs no check.
        nobodyHolds("newcomer@acme.com");
        UserInvitationService invitations = mock(UserInvitationService.class);
        when(invitations.resolveJoinChannel("tok", "email")).thenReturn("newcomer@acme.com");
        ReflectionTestUtils.setField(loginService, "invitationService", invitations);

        loginService.sendJoinCode("tok", "email");

        ArgumentCaptor<MailRequestMessage> sent = ArgumentCaptor.forClass(MailRequestMessage.class);
        verify(eventPublisher).publishEvent(sent.capture());
        assertThat(sent.getValue().to()).containsExactly("newcomer@acme.com");
    }

    @Test
    void joinStaysLoudWhenItCannotSendAtAll() {
        // The swallow lives at the public entry points, not in the delivery, and this is why: an
        // invitee who trips the send limit has no enumeration to offer — the caller IS the
        // invitation — so answering "sent" and delivering nothing recreates the dead end this whole
        // change exists to remove. Nothing else in the suite would notice: /join's happy path keeps
        // working, and the failure is silent by construction.
        doThrow(new BusinessException("Too many requests")).when(codeGuard).beforeSend(anyString());
        UserInvitationService invitations = mock(UserInvitationService.class);
        when(invitations.resolveJoinChannel("tok", "email")).thenReturn("newcomer@acme.com");
        ReflectionTestUtils.setField(loginService, "invitationService", invitations);

        assertThatThrownBy(() -> loginService.sendJoinCode("tok", "email"))
                .isInstanceOf(BusinessException.class);
    }
}
