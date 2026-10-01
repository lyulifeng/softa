package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.service.UserAccountService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Reading your own profile must find it through the PERSON, not through one of their memberships.
 *
 * <p>{@code Context} carries the account id — which membership is in use right now — and a person
 * may hold several. The profile is one row for the person, and {@code UserAccount.profileId} is
 * what points at it.
 *
 * <p>The reads used to filter on {@code UserProfile.userId}, a leftover from the 1:1 era that the
 * field itself describes as "kept only so the data migration can map the old pairing". It names at
 * most ONE of a person's memberships, so signing in through any other one answered
 * {@code "Current user profile not found."} while the row sat in the table. A consultant meets this
 * every time: they are created by the platform and then granted into a customer's tenant, so the
 * membership they sign in with is never the one the back-pointer happens to name.
 */
class MyProfileResolvesByPersonTest {

    /** The membership in use right now — one of several this person holds. */
    private static final Long ACCOUNT_IN_USE = 895974606685339653L;
    /** The person. The profile row's own id, and what every membership of theirs points at. */
    private static final Long PROFILE = 886383063787921410L;
    /** The membership the legacy back-pointer happens to name — deliberately not the one in use. */
    private static final Long OTHER_ACCOUNT = 886383063213301761L;

    private final UserAccountService accountService = mock(UserAccountService.class);
    private final UserProfileServiceImpl profileService = spy(new UserProfileServiceImpl());

    MyProfileResolvesByPersonTest() {
        ReflectionTestUtils.setField(profileService, "accountService", accountService);
    }

    private static UserAccount membershipInUse() {
        UserAccount account = new UserAccount();
        account.setId(ACCOUNT_IN_USE);
        account.setProfileId(PROFILE);
        return account;
    }

    private static UserProfile profileRow() {
        UserProfile profile = new UserProfile();
        profile.setId(PROFILE);
        // Pointing at the OTHER membership, which is exactly the state the migration leaves behind
        // and exactly what the old filter would have compared against.
        profile.setUserId(OTHER_ACCOUNT);
        return profile;
    }

    private <T> T asUser(Long userId, java.util.function.Supplier<T> call) {
        Context context = new Context();
        context.setUserId(userId);
        return ContextHolder.callWith(context, call::get);
    }

    @Test
    void theProfileIsFoundThroughTheMembershipInUse() {
        when(accountService.getById(ACCOUNT_IN_USE)).thenReturn(Optional.of(membershipInUse()));
        doReturn(Optional.of(profileRow())).when(profileService).searchOne(any(Filters.class));

        UserProfile found = asUser(ACCOUNT_IN_USE, profileService::getCurrentUserProfile);

        assertThat(found.getId()).isEqualTo(PROFILE);

        // The filter is the whole point: keyed on the person, never on the membership in use and
        // never on the back-pointer. Asserted on the filter rather than on the result, because a
        // stubbed search returns the row whatever it was asked for.
        ArgumentCaptor<Filters> filters = ArgumentCaptor.forClass(Filters.class);
        verify(profileService).searchOne(filters.capture());
        String asked = filters.getValue().toString();
        assertThat(asked).contains(String.valueOf(PROFILE));
        assertThat(asked).doesNotContain(String.valueOf(OTHER_ACCOUNT));
    }

    @Test
    void anUnresolvableSessionIsRefusedRatherThanFilteredOnNull() {
        // A null id must not reach the query: what it does to an equality filter is the ORM's
        // business, and "matches everything" would hand back somebody else's profile.
        when(accountService.getById(ACCOUNT_IN_USE)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> asUser(ACCOUNT_IN_USE, profileService::getCurrentUserProfile))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Current user profile not found.");

        verify(profileService, org.mockito.Mockito.never()).searchOne(any(Filters.class));
    }
}
