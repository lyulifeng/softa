package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.service.UserIdentityService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/**
 * One spelling for a login identifier, wherever it is stored, looked up or hashed.
 *
 * <p>The three used to disagree — the unknown-identifier counter hashed a trimmed, lowercased form
 * while the identity lookup queried the raw string — and the disagreement was an existence oracle
 * at the login form (see {@link PasswordLockoutTest}). What is asserted here is the other half of
 * the fix: the seeding writes the same form the lookup queries, so an identifier can be found by
 * any spelling of itself.
 */
class LoginIdentifiersTest {

    @Test
    void normalisation_trimsAndLowercases_andLeavesADialCodeMobileAlone() {
        assertThat(LoginIdentifiers.normalize("  NOBODY@Acme.com ")).isEqualTo("nobody@acme.com");
        assertThat(LoginIdentifiers.normalize(" +6591234567 ")).isEqualTo("+6591234567");
        assertThat(LoginIdentifiers.normalize("   ")).isNull();
        assertThat(LoginIdentifiers.normalize(null)).isNull();
    }

    @Test
    void theLookup_queriesTheCanonicalForm_notWhatWasTyped() {
        // The known branch of the login path: a leading space or a capital must reach the database
        // as the value the seeding wrote, or the row is never found and the identifier "does not
        // exist" for exactly as long as it is misspelt.
        UserIdentityServiceImpl identityService = spy(new UserIdentityServiceImpl(mock(CacheService.class)));
        doReturn(Optional.empty()).when(identityService).searchOne(any(Filters.class));

        identityService.findByLoginIdentifier(" ALICE@acme.com");

        ArgumentCaptor<Filters> filters = ArgumentCaptor.forClass(Filters.class);
        verify(identityService, times(2)).searchOne(filters.capture());
        assertThat(filters.getAllValues())
                .allSatisfy(f -> assertThat(f.toString()).contains("\"alice@acme.com\"").doesNotContain("ALICE"));
    }

    @Test
    void theClaimCheck_asksInTheCanonicalForm() {
        UserIdentityServiceImpl identityService = spy(new UserIdentityServiceImpl(mock(CacheService.class)));
        doReturn(List.of()).when(identityService).searchList(any(Filters.class));

        identityService.isIdentifierClaimable(" ALICE@acme.com", 7L);

        ArgumentCaptor<Filters> filters = ArgumentCaptor.forClass(Filters.class);
        verify(identityService).searchList(filters.capture());
        assertThat(filters.getValue().toString()).contains("\"alice@acme.com\"");
    }

    @Test
    void aPersonMintedOnJoin_getsTheIdentifierInCanonicalForm() {
        // The address the invitation carries is the work contact as HR typed it. The identifier
        // seeded from it is what login will query, so it is written the way login will ask.
        UserIdentityService identityService = mock(UserIdentityService.class);
        UserProfileServiceImpl profileService = spy(new UserProfileServiceImpl());
        ReflectionTestUtils.setField(profileService, "identityService", identityService);
        doReturn(42L).when(profileService).createOne(any(UserProfile.class));

        profileService.createPersonForJoin(" Ada@Acme.com ");

        ArgumentCaptor<UserIdentity> seeded = ArgumentCaptor.forClass(UserIdentity.class);
        verify(identityService).createOne(seeded.capture());
        assertThat(seeded.getValue().getLoginEmail()).isEqualTo("ada@acme.com");
        assertThat(seeded.getValue().getProfileId()).isEqualTo(42L);
    }
}
