package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.user.entity.UserIdentity;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;

/**
 * The two identifier rules that live in the identity service.
 *
 * <p>{@code adoptIdentifier} heals rows that predate identifier seeding — but only ever fills an
 * EMPTY slot. Overwriting would mean that proving control of one address rewrites another, which
 * is an account-takeover primitive rather than a backfill.
 *
 * <p>{@code findByLoginIdentifier} refuses when several people claim one identifier. The columns
 * carry no unique index yet, so the database cannot rule it out, and picking a row would sign
 * someone in as a person who merely shares their phone number.
 */
class IdentityAdoptionTest {

    private final CacheService cacheService = mock(CacheService.class);
    private final UserIdentityServiceImpl identityService =
            spy(new UserIdentityServiceImpl(cacheService));

    private static UserIdentity identity(String loginEmail, String loginMobile) {
        UserIdentity identity = new UserIdentity();
        identity.setId(11L);
        identity.setProfileId(7L);
        identity.setLoginEmail(loginEmail);
        identity.setLoginMobile(loginMobile);
        return identity;
    }

    @Test
    void anEmptyEmailSlot_isFilled() {
        UserIdentity person = identity(null, null);
        doReturn(List.of()).when(identityService).searchList(any(Filters.class));
        doReturn(true).when(identityService).updateOne(person);

        identityService.adoptIdentifier(person, "alice@acme.com");

        assertThat(person.getLoginEmail()).isEqualTo("alice@acme.com");
        verify(identityService).updateOne(person);
    }

    @Test
    void anEmptyMobileSlot_isFilled_andEmailIsLeftAlone() {
        UserIdentity person = identity(null, null);
        doReturn(List.of()).when(identityService).searchList(any(Filters.class));
        doReturn(true).when(identityService).updateOne(person);

        identityService.adoptIdentifier(person, "+8613800138000");

        assertThat(person.getLoginMobile()).isEqualTo("+8613800138000");
        assertThat(person.getLoginEmail()).isNull();
    }

    @Test
    void anIdentifierAlreadyOnFile_isNeverOverwritten() {
        // The security property: proving control of one address must not rewrite another.
        UserIdentity person = identity("personal@gmail.com", null);

        identityService.adoptIdentifier(person, "alice@acme.com");

        assertThat(person.getLoginEmail()).isEqualTo("personal@gmail.com");
        verify(identityService, never()).updateOne(any(UserIdentity.class));
    }

    @Test
    void anIdentifierAnotherPersonAlreadyLogsInWith_isNotClaimed() {
        // The 27-accounts-on-one-work-number case. Copying the contact across does not create a
        // login route, it destroys one: both holders would then resolve to "shared by more than one
        // account" instead of the one who had it to themselves.
        UserIdentity mine = identity(null, null);
        UserIdentity someoneElse = identity(null, "+8613800138000");
        someoneElse.setId(99L);
        someoneElse.setProfileId(8L);
        doReturn(List.of(someoneElse)).when(identityService).searchList(any(Filters.class));

        identityService.adoptIdentifier(mine, "+8613800138000");

        assertThat(mine.getLoginMobile()).isNull();
        verify(identityService, never()).updateOne(any(UserIdentity.class));
    }

    @Test
    void reClaimingMyOwnIdentifier_isFine() {
        // Only OTHER people block a claim. A row that already names this same person is not a
        // conflict, and treating it as one would refuse a legitimate re-seed.
        UserIdentity mine = identity(null, null);
        UserIdentity alsoMine = identity(null, "+8613800138000");
        doReturn(List.of(alsoMine)).when(identityService).searchList(any(Filters.class));
        doReturn(true).when(identityService).updateOne(mine);

        identityService.adoptIdentifier(mine, "+8613800138000");

        assertThat(mine.getLoginMobile()).isEqualTo("+8613800138000");
    }

    @Test
    void aSharedIdentifier_isRefused_notGuessed() {
        // searchOne throws IllegalArgumentException on more than one row, quoting the filter —
        // which would surface at an anonymous endpoint as a server error naming someone's address.
        doThrow(new IllegalArgumentException("more than 1"))
                .when(identityService).searchOne(any(Filters.class));

        assertThatThrownBy(() -> identityService.findByLoginIdentifier("shared@acme.com"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("shared by more than one account");
    }

    @Test
    void aBlankIdentifier_resolvesToNobody() {
        assertThat(identityService.findByLoginIdentifier("  ")).isEqualTo(Optional.empty());
    }
}
