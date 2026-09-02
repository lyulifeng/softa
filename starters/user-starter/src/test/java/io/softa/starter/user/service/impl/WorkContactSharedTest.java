package io.softa.starter.user.service.impl;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.spy;

/**
 * "Shared work contact" counts PEOPLE, not accounts.
 *
 * <p>The guard exists because a code sent to an address shared by several employees identifies
 * none of them (finding #2). But one person employed by two companies has two accounts carrying
 * the same personal address — that is multi-company working as designed, and reading it as
 * "shared" makes A5 impossible to use. Counting accounts conflated the two; counting people
 * separates them.
 */
class WorkContactSharedTest {

    private final UserAccountServiceImpl accountService = spy(new UserAccountServiceImpl());

    private static UserAccount account(Long id, Long profileId) {
        UserAccount a = new UserAccount();
        a.setId(id);
        a.setProfileId(profileId);
        return a;
    }

    private void givenMatches(UserAccount... accounts) {
        doReturn(List.of(accounts)).when(accountService).searchList(any(Filters.class));
    }

    @Test
    void onePersonInTwoCompanies_isNotShared() {
        // The regression this test exists for: multi-company login broke because the same person's
        // two memberships were counted as two claimants of their own address.
        givenMatches(account(1L, 7L), account(2L, 7L));

        assertThat(accountService.isWorkContactShared("alice@acme.com")).isFalse();
    }

    @Test
    void twoDifferentPeople_isShared() {
        givenMatches(account(1L, 7L), account(2L, 8L));

        assertThat(accountService.isWorkContactShared("+8613800138000")).isTrue();
    }

    @Test
    void aBoundPersonPlusAnUnboundAccount_isShared() {
        // The attack shape: A holds the number as a login identifier, B's account carries the same
        // number and no person yet. Resolving the address would hand B the identity of A.
        givenMatches(account(1L, 7L), account(2L, null));

        assertThat(accountService.isWorkContactShared("+8613800138000")).isTrue();
    }

    @Test
    void twoUnboundAccounts_isShared() {
        givenMatches(account(1L, null), account(2L, null));

        assertThat(accountService.isWorkContactShared("+8613800138000")).isTrue();
    }

    @Test
    void aSingleAccount_isNotShared() {
        givenMatches(account(1L, 7L));

        assertThat(accountService.isWorkContactShared("alice@acme.com")).isFalse();
    }

    @Test
    void anAccountWhoseEmailAndMobileAreTheSameString_isCountedOnce() {
        // Both lookups return the same row; without de-duplicating by account id it would look
        // like two claimants and refuse a perfectly ordinary account.
        UserAccount same = account(1L, 7L);
        givenMatches(same);

        assertThat(accountService.isWorkContactShared("same-value")).isFalse();
    }

    @Test
    void aBlankContact_isNotShared() {
        assertThat(accountService.isWorkContactShared("  ")).isFalse();
        assertThat(accountService.isWorkContactShared(null)).isFalse();
    }
}
