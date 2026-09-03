package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.when;

/**
 * Whether the forced Set Password step survives a page reload.
 *
 * <p>Authentication reports {@code mustSetPassword} once, in its response. A client that only
 * remembers it from there loses the requirement on refresh — and since a password-less person
 * cannot come back through the password route, that does not postpone a screen, it locks them out
 * of their own account. This is the session-based answer that makes the step recoverable.
 */
class MustSetMyPasswordTest {

    private static final Long USER = 100L;
    private static final Long PROFILE = 7L;

    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserAccountServiceImpl accountService = spy(new UserAccountServiceImpl());

    MustSetMyPasswordTest() {
        ReflectionTestUtils.setField(accountService, "identityService", identityService);
    }

    /** ContextHolder is a ScopedValue — a session exists only inside runWith/callWith. */
    private boolean asUser(Long userId) {
        Context context = new Context();
        context.setUserId(userId);
        return ContextHolder.callWith(context, accountService::mustSetMyPassword);
    }

    private static UserAccount account(Long profileId) {
        UserAccount account = new UserAccount();
        account.setId(USER);
        account.setProfileId(profileId);
        return account;
    }

    private static UserIdentity identity(String password) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(PROFILE);
        identity.setPassword(password);
        return identity;
    }

    @Test
    void aPersonWithNoPassword_stillOwesOne() {
        doReturn(Optional.of(account(PROFILE))).when(accountService).getById(USER);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(identity(null)));

        assertThat(asUser(USER)).isTrue();
    }

    @Test
    void aPersonWhoHasOne_owesNothing() {
        doReturn(Optional.of(account(PROFILE))).when(accountService).getById(USER);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.of(identity("hash")));

        assertThat(asUser(USER)).isFalse();
    }

    @Test
    void noCredentialsRowAtAll_doesNotForceAScreenThatWouldBeRefused() {
        // A data fault, not a password-less person. Forcing the step here would put someone in
        // front of a form whose own endpoint (requireIdentity) throws.
        doReturn(Optional.of(account(PROFILE))).when(accountService).getById(USER);
        when(identityService.findByProfile(PROFILE)).thenReturn(Optional.empty());

        assertThat(asUser(USER)).isFalse();
    }

    @Test
    void noSession_owesNothing() {
        assertThat(asUser(null)).isFalse();
    }
}
