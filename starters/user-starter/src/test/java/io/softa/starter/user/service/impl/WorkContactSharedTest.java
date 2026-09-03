package io.softa.starter.user.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.service.UserRoleRelService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

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
    void theQueryCarriesTheTrimmedContact_inTheFormTheColumnsHold() {
        // What reaches the database is what the write paths store: trimmed, case kept. Lowercasing
        // the query would not help (the column is not lowercased); case-folding is the collation's.
        givenMatches();

        accountService.isWorkContactShared(" Ada@Acme.com ");

        ArgumentCaptor<Filters> filters = ArgumentCaptor.forClass(Filters.class);
        verify(accountService, times(2)).searchList(filters.capture());
        assertThat(filters.getAllValues())
                .allSatisfy(f -> assertThat(f.toString()).contains("\"Ada@Acme.com\""));
    }

    @Test
    void twoAccountsHoldingOneAddress_readAsShared_evenWhenHRTypedOneWithASpace() {
        // The guard's bypass: HR typed " ada@acme.com" onto one row and "ada@acme.com" onto another.
        // The query is exact (a leading space is a different string to the database), so an
        // untrimmed stored value never matched — a genuinely shared address read as unshared, and a
        // code sent to it identified "one" person. Trimming at write time is what closes it, so the
        // rows are written through a write path and the store answers by exact equality.
        ReflectionTestUtils.setField(accountService, "roleRelService", mock(UserRoleRelService.class));
        List<UserAccount> store = new ArrayList<>();
        doAnswer(inv -> {
            String query = inv.getArgument(0).toString();
            return store.stream().filter(a -> query.contains("\"" + a.getEmail() + "\"")).toList();
        }).when(accountService).searchList(any(Filters.class));

        UserAccount first = account(1L, 7L);
        accountService.reviveWith(first, " ada@acme.com", null);
        UserAccount second = account(2L, null);
        accountService.reviveWith(second, "ada@acme.com", null);
        store.add(first);
        store.add(second);

        assertThat(accountService.isWorkContactShared("ada@acme.com")).isTrue();
    }

    @Test
    void aMobileTypedWithSeparators_isAskedAboutAsTheBareNumber() {
        // The columns hold the collapsed form (workContact), so the question has to be asked in it:
        // "+65 9123-4567" and "+6591234567" are one number held by however many rows carry it.
        givenMatches();

        accountService.isWorkContactShared("+65 9123-4567");

        ArgumentCaptor<Filters> filters = ArgumentCaptor.forClass(Filters.class);
        verify(accountService, times(2)).searchList(filters.capture());
        assertThat(filters.getAllValues())
                .allSatisfy(f -> assertThat(f.toString()).contains("\"+6591234567\"").doesNotContain("9123-"));
    }

    @Test
    void twoAccountsHoldingOneMobile_readAsShared_whenHRTypedThemDifferently() {
        // The bypass on the mobile side: one row written as "+65 9123-4567", the other as
        // "+6591234567". Stored as typed, the exact query matched one row each and the number read
        // as unshared — the exact case the guard exists for. Collapsing at write time closes it.
        ReflectionTestUtils.setField(accountService, "roleRelService", mock(UserRoleRelService.class));
        List<UserAccount> store = new ArrayList<>();
        doAnswer(inv -> {
            String query = inv.getArgument(0).toString();
            return store.stream().filter(a -> query.contains("\"" + a.getMobile() + "\"")).toList();
        }).when(accountService).searchList(any(Filters.class));

        UserAccount first = account(1L, 7L);
        accountService.reviveWith(first, null, "+65 9123-4567");
        UserAccount second = account(2L, null);
        accountService.reviveWith(second, null, "+6591234567");
        store.add(first);
        store.add(second);

        assertThat(first.getMobile()).isEqualTo("+6591234567");
        assertThat(accountService.isWorkContactShared("+6591234567")).isTrue();
    }

    @Test
    void aBlankContact_isNotShared() {
        assertThat(accountService.isWorkContactShared("  ")).isFalse();
        assertThat(accountService.isWorkContactShared(null)).isFalse();
    }
}
