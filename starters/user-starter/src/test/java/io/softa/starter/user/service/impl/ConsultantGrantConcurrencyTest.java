package io.softa.starter.user.service.impl;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.CacheService;
import io.softa.starter.user.entity.ConsultantAuthorization;
import io.softa.starter.user.entity.ConsultantProfile;
import io.softa.starter.user.service.ConsultantService;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Two operators with the consultant form open at once.
 *
 * <p>The save used to carry the table the screen was showing and make the stored rows match it, so
 * every save asserted the whole truth — about the rows the operator had edited and equally about
 * the rows they had never looked at. Two people therefore overwrote one another while editing
 * nothing in common: the one who saved second carried the other's rows at the values their page had
 * loaded, and put them back. Nothing warned either of them, and the audit log recorded the second
 * save as an edit to a row that operator never touched.
 *
 * <p>Taking the changes instead of the table removes the overwrite at its source rather than
 * guarding it: an untouched row is simply absent, so there is nothing to write back. What is left
 * is two operators who moved the SAME grant's date, which is a real disagreement and goes to the
 * later save — the same as every other child table here.
 */
class ConsultantGrantConcurrencyTest {

    private static final Long PROFILE = 7L;
    private static final Long ACME = 3L;
    private static final Long GLOBEX = 4L;
    private static final Long ACME_GRANT = 11L;
    private static final Long GLOBEX_GRANT = 12L;

    private static final LocalDate SEPTEMBER = LocalDate.of(2026, 9, 28);
    private static final LocalDate OCTOBER = LocalDate.of(2026, 10, 30);

    private ConsultantServiceImpl service;
    private ConsultantAuthorizationService authorizationService;
    private UserAccountService accountService;

    @BeforeEach
    void setUp() {
        service = spy(new ConsultantServiceImpl());
        authorizationService = mock(ConsultantAuthorizationService.class);
        accountService = mock(UserAccountService.class);
        ReflectionTestUtils.setField(service, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(service, "accountService", accountService);
        ReflectionTestUtils.setField(service, "cacheService", mock(CacheService.class));
        ReflectionTestUtils.setField(service, "profileService", mock(UserProfileService.class));
        ReflectionTestUtils.setField(service, "identityService", mock(UserIdentityService.class));

        ConsultantProfile profile = new ConsultantProfile();
        profile.setId(1L);
        profile.setProfileId(PROFILE);
        profile.setActive(Boolean.TRUE);
        doReturn(Optional.of(profile)).when(service).searchOne(any(Filters.class));
        when(accountService.listMembershipsOf(PROFILE)).thenReturn(List.of());

        // What both operators' pages loaded: one grant per company, Acme's ending in September.
        when(authorizationService.searchList(any(Filters.class)))
                .thenReturn(List.of(stored(ACME_GRANT, ACME, SEPTEMBER), stored(GLOBEX_GRANT, GLOBEX, null)));
    }

    @Test
    void anOperatorWhoChangedNothingWritesNothing() {
        // The reported failure, end to end. The second operator opened the form, touched nothing
        // and pressed Save; their page still held the September date the first operator had just
        // moved to October, and the save put it back.
        service.applyAuthorizations(PROFILE, ConsultantService.AuthorizationChanges.none());

        verify(authorizationService, never()).updateOne(any(ConsultantAuthorization.class));
        verify(authorizationService, never()).updateOne(any(ConsultantAuthorization.class), anyBoolean());
        verify(authorizationService, never()).createOne(any(ConsultantAuthorization.class));
        verify(authorizationService, never()).deleteById(any());
    }

    @Test
    void movingOneGrantLeavesEveryOtherRowAlone() {
        // The first operator's own save: one row named, one row written. The second company's
        // grant is not in the payload at all, so it cannot be rewritten by being carried along.
        service.applyAuthorizations(PROFILE, redating(ACME_GRANT, OCTOBER));

        ArgumentCaptor<ConsultantAuthorization> written =
                ArgumentCaptor.forClass(ConsultantAuthorization.class);
        verify(authorizationService).updateOne(written.capture(), eq(false));
        assertThat(written.getValue().getId()).isEqualTo(ACME_GRANT);
        assertThat(written.getValue().getEndDate()).isEqualTo(OCTOBER);
    }

    @Test
    void makingAGrantOpenEndedWritesTheNullRatherThanSkippingIt() {
        // "No end" is the one edit whose new value is null, and the plain updateOne skips nulls —
        // so this saved without complaint and left September in place. The write has to carry the
        // null, which is what ignoreNull=false is for.
        service.applyAuthorizations(PROFILE, redating(ACME_GRANT, null));

        ArgumentCaptor<ConsultantAuthorization> written =
                ArgumentCaptor.forClass(ConsultantAuthorization.class);
        verify(authorizationService).updateOne(written.capture(), eq(false));
        assertThat(written.getValue().getId()).isEqualTo(ACME_GRANT);
        assertThat(written.getValue().getEndDate()).isNull();
        // The rest of the row goes back as it was read, so writing nulls clears nothing else.
        assertThat(written.getValue().getTenantId()).isEqualTo(ACME);
        assertThat(written.getValue().getAccountId()).isEqualTo(500L);
        verify(authorizationService, never()).updateOne(any(ConsultantAuthorization.class));
    }

    @Test
    void twoOperatorsMovingDifferentGrantsDoNotCollide() {
        // Each names only what they moved, so the two saves compose: neither carries the other's
        // row, and the order they land in stops mattering.
        service.applyAuthorizations(PROFILE, redating(ACME_GRANT, OCTOBER));
        service.applyAuthorizations(PROFILE, redating(GLOBEX_GRANT, SEPTEMBER));

        ArgumentCaptor<ConsultantAuthorization> written =
                ArgumentCaptor.forClass(ConsultantAuthorization.class);
        verify(authorizationService, org.mockito.Mockito.times(2)).updateOne(written.capture(), eq(false));
        assertThat(written.getAllValues()).extracting(ConsultantAuthorization::getId)
                .containsExactly(ACME_GRANT, GLOBEX_GRANT);
    }

    @Test
    void redatingAGrantSomebodyElseRevokedIsRefused() {
        // The other direction, and the only concurrent edit that can be detected at all: the row is
        // gone, so there is nothing left to compare against — but its absence is itself the answer.
        // Silently recreating it would re-admit a consultant the other operator had just shut out.
        assertThatThrownBy(() -> service.applyAuthorizations(PROFILE, redating(99L, OCTOBER)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Refresh and try again");

        verify(authorizationService, never()).updateOne(any(ConsultantAuthorization.class));
        verify(authorizationService, never()).updateOne(any(ConsultantAuthorization.class), anyBoolean());
    }

    @Test
    void revokingAGrantSomebodyElseAlreadyRevokedIsRefused() {
        assertThatThrownBy(() -> service.applyAuthorizations(PROFILE,
                new ConsultantService.AuthorizationChanges(List.of(), List.of(), List.of(99L))))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Refresh and try again");

        verify(authorizationService, never()).deleteById(any());
    }

    @Test
    void nothingIsWrittenBeforeTheRefusal() {
        // A payload that names a stale row alongside a good one is refused whole. Applying the
        // half that still makes sense would leave the operator looking at an error beside changes
        // that silently went through.
        ConsultantAuthorization added = new ConsultantAuthorization();
        added.setTenantId(5L);
        service.applyAuthorizations(PROFILE, ConsultantService.AuthorizationChanges.none());

        assertThatThrownBy(() -> service.applyAuthorizations(PROFILE,
                new ConsultantService.AuthorizationChanges(List.of(added), List.of(), List.of(99L))))
                .isInstanceOf(BusinessException.class);

        verify(authorizationService, never()).createOne(any(ConsultantAuthorization.class));
    }

    @Test
    void addingACompanyThatIsAlreadyAuthorizedIsRefused() {
        // Checked against what will be left standing rather than against the payload alone: the
        // duplicate is with a row the payload never mentions, which a check over the submitted
        // rows could not see.
        ConsultantAuthorization again = new ConsultantAuthorization();
        again.setTenantId(ACME);

        assertThatThrownBy(() -> service.applyAuthorizations(PROFILE, granting(again)))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("one grant per company");
    }

    @Test
    void revokingACompanyAndAddingItBackInOneSaveIsAllowed() {
        // The same save may let a company go and take it again — re-dating by hand, or an operator
        // who removed the row and re-added it. Counting the revoked row as still standing would
        // refuse that as a duplicate.
        ConsultantAuthorization again = new ConsultantAuthorization();
        again.setTenantId(ACME);
        when(accountService.findMembershipInTenant(ACME, PROFILE)).thenReturn(Optional.empty());

        service.applyAuthorizations(PROFILE, new ConsultantService.AuthorizationChanges(
                List.of(again), List.of(), List.of(ACME_GRANT)));

        verify(authorizationService).createOne(any(ConsultantAuthorization.class));
        verify(authorizationService).deleteById(ACME_GRANT);
    }

    private static ConsultantAuthorization stored(Long id, Long tenantId, LocalDate endDate) {
        ConsultantAuthorization grant = new ConsultantAuthorization();
        grant.setId(id);
        grant.setProfileId(PROFILE);
        grant.setTenantId(tenantId);
        grant.setEndDate(endDate);
        grant.setAccountId(500L);
        return grant;
    }

    /** A save that moves one grant's end date and says nothing about any other row. */
    private static ConsultantService.AuthorizationChanges redating(Long grantId, LocalDate endDate) {
        ConsultantAuthorization moved = new ConsultantAuthorization();
        moved.setId(grantId);
        moved.setEndDate(endDate);
        return new ConsultantService.AuthorizationChanges(List.of(), List.of(moved), List.of());
    }

    /** A save that adds grants and touches nothing else. */
    private static ConsultantService.AuthorizationChanges granting(ConsultantAuthorization... rows) {
        return new ConsultantService.AuthorizationChanges(List.of(rows), List.of(), List.of());
    }
}
