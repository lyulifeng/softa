package io.softa.starter.user.service.impl;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Re-hiring a leaver through the ordinary create path (PRD S-D).
 *
 * <p>Every creation path funnels through {@code registerInvitedUser}, and it refused whenever the
 * person had ANY membership here — a DEACTIVATED one included. So a former employee could never be
 * added back: Add Employee said "already a member" and /join pointed at a re-hire flow that did
 * not exist. {@code uk_user_account_tenant_profile} forbids a second row per (tenant, person), so
 * the only correct shape is to revive the closed row, and it is the create path that has to do it.
 */
class RehireTest {

    private static final Long PERSON = 7L;
    private static final Long THIS_TENANT = 2L;
    private static final Long CLOSED_ROW = 999L;
    private static final String OLD_EMAIL = "ada@acme.com";
    private static final String NEW_EMAIL = "ada.l@acme.com";
    private static final String MOBILE = "+6591234567";

    private final UserIdentityService identityService = mock(UserIdentityService.class);
    private final UserProfileService profileService = mock(UserProfileService.class);
    private final UserAccountServiceImpl accountService = spy(new UserAccountServiceImpl());

    RehireTest() {
        ReflectionTestUtils.setField(accountService, "identityService", identityService);
        ReflectionTestUtils.setField(accountService, "profileService", profileService);
        doReturn(1L).when(accountService).createOne(any(UserAccount.class));
        doReturn(List.of()).when(accountService).searchList(any(Filters.class));
        doReturn(Optional.empty()).when(accountService).findMembershipInTenant(any(), any());
        when(profileService.registerUserProfile(anyLong(), any(UserProfileDTO.class)))
                .thenReturn(new UserInfo());
        when(profileService.linkAccountToPerson(anyLong(), anyLong())).thenReturn(new UserInfo());
        when(profileService.getUserInfo(anyLong())).thenReturn(new UserInfo());
        // The person is known by every identifier used here.
        UserIdentity identity = new UserIdentity();
        identity.setProfileId(PERSON);
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.of(identity));
    }

    private void inThisTenant(Runnable body) {
        Context ctx = new Context();
        ctx.setTenantId(THIS_TENANT);
        ContextHolder.runWith(ctx, body);
    }

    private static UserAccount membership(AccountStatus status, String email) {
        UserAccount account = new UserAccount();
        account.setId(CLOSED_ROW);
        account.setTenantId(THIS_TENANT);
        account.setProfileId(PERSON);
        account.setStatus(status);
        account.setEmail(email);
        return account;
    }

    private void personHasMembershipHere(AccountStatus status) {
        UserAccount row = membership(status, OLD_EMAIL);
        doReturn(Optional.of(row)).when(accountService).findMembershipInTenant(THIS_TENANT, PERSON);
        doReturn(Optional.of(row)).when(accountService).reviveMembership(PERSON, NEW_EMAIL, MOBILE);
    }

    @Test
    void aLeaverReHiredThroughCreateRevivesTheClosedMembershipInsteadOfRefusing() {
        personHasMembershipHere(AccountStatus.DEACTIVATED);

        inThisTenant(() -> assertThatCode(() -> accountService.registerInvitedUser(NEW_EMAIL, MOBILE, "Ada L"))
                .doesNotThrowAnyException());

        // The row is reused with the NEW contacts — no second row, no second person.
        verify(accountService).reviveMembership(PERSON, NEW_EMAIL, MOBILE);
        verify(profileService).getUserInfo(CLOSED_ROW);
        verify(accountService, never()).createOne(any(UserAccount.class));
        verify(profileService, never()).registerUserProfile(anyLong(), any(UserProfileDTO.class));
        verify(profileService, never()).linkAccountToPerson(anyLong(), anyLong());
    }

    @Test
    void aStillActiveMemberHereIsStillRefused() {
        personHasMembershipHere(AccountStatus.ACTIVE);

        inThisTenant(() -> assertThatThrownBy(() -> accountService.registerInvitedUser(NEW_EMAIL, MOBILE, "Ada L"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already a member of this company"));

        verify(accountService, never()).reviveMembership(any(), any(), any());
        verify(accountService, never()).createOne(any(UserAccount.class));
    }

    @Test
    void anInvitedMemberHereIsStillRefused() {
        // Only a CLOSED membership is revivable; an invitation in flight is a live membership.
        personHasMembershipHere(AccountStatus.INVITED);

        inThisTenant(() -> assertThatThrownBy(() -> accountService.registerInvitedUser(NEW_EMAIL, MOBILE, "Ada L"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already a member of this company"));

        verify(accountService, never()).reviveMembership(any(), any(), any());
    }

    @Test
    void theClosedRowsOwnContactDoesNotCountAsASameTenantDuplicate() {
        // The leaver's old work address still sits on their DEACTIVATED row. Re-hiring them under
        // the same address must not trip "Email already exists" on their own closed membership.
        UserAccount closed = membership(AccountStatus.DEACTIVATED, OLD_EMAIL);
        doReturn(Optional.of(closed)).when(accountService).findMembershipInTenant(THIS_TENANT, PERSON);
        doReturn(List.of(closed)).when(accountService).searchList(any(Filters.class));
        doReturn(Optional.of(closed)).when(accountService).reviveMembership(PERSON, OLD_EMAIL, null);

        inThisTenant(() -> assertThatCode(() -> accountService.registerInvitedUser(OLD_EMAIL, null, "Ada L"))
                .doesNotThrowAnyException());

        verify(accountService).reviveMembership(PERSON, OLD_EMAIL, null);
    }

    // ─── the canonical leaver: their login identifier was released at off-boarding ───

    private static UserAccount closedRowInTenant(Long tenantId, Long profileId, String email, String mobile) {
        UserAccount row = new UserAccount();
        row.setId(CLOSED_ROW);
        row.setTenantId(tenantId);
        row.setProfileId(profileId);
        row.setStatus(AccountStatus.DEACTIVATED);
        row.setEmail(email);
        row.setMobile(mobile);
        return row;
    }

    /** Only a query that names THIS tenant gets an answer; anything wider sees the other tenant's row. */
    private static boolean namesThisTenant(Filters filters) {
        return filters != null && filters.toString().contains("[\"tenantId\",\"=\"," + THIS_TENANT + "]");
    }

    @Test
    void aLeaverWhoseLoginEmailWasReleasedIsStillRevivedFromTheirClosedRow() {
        // offBoardWith → releaseLoginIdentifiers nulls identity.loginEmail when it equals the work
        // email, so the identity lookup finds nobody. The DEACTIVATED row still carries the address
        // AND the person — it is what must anchor the re-hire, or the leaver's own old address reads
        // as "Email already exists".
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());
        UserAccount closed = closedRowInTenant(THIS_TENANT, PERSON, OLD_EMAIL, null);
        doReturn(List.of(closed)).when(accountService).searchList(any(Filters.class));
        doReturn(Optional.of(closed)).when(accountService).reviveMembership(PERSON, OLD_EMAIL, MOBILE);

        inThisTenant(() -> assertThatCode(() -> accountService.registerInvitedUser(OLD_EMAIL, MOBILE, "Ada L"))
                .doesNotThrowAnyException());

        verify(accountService).reviveMembership(PERSON, OLD_EMAIL, MOBILE);
        verify(profileService).getUserInfo(CLOSED_ROW);
        verify(accountService, never()).createOne(any(UserAccount.class));
        verify(profileService, never()).registerUserProfile(anyLong(), any(UserProfileDTO.class));
        verify(profileService, never()).linkAccountToPerson(anyLong(), anyLong());
    }

    @Test
    void aReleasedLeaverCanBeAnchoredByTheirOldMobileWhileGettingANewEmail() {
        // HR re-hires under a new work address but the same phone: the mobile finds the closed row,
        // and the new email is then checked for same-tenant holders EXCLUDING that row.
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());
        UserAccount closed = closedRowInTenant(THIS_TENANT, PERSON, OLD_EMAIL, MOBILE);
        doReturn(List.of(closed)).when(accountService).searchList(any(Filters.class));
        doReturn(Optional.of(closed)).when(accountService).reviveMembership(PERSON, NEW_EMAIL, MOBILE);

        inThisTenant(() -> assertThatCode(() -> accountService.registerInvitedUser(NEW_EMAIL, MOBILE, "Ada L"))
                .doesNotThrowAnyException());

        verify(accountService).reviveMembership(PERSON, NEW_EMAIL, MOBILE);
        verify(accountService, never()).createOne(any(UserAccount.class));
    }

    @Test
    void aClosedRowInAnotherTenantDoesNotAnchorTheReHire() {
        // The leaver of company 9 is a stranger to company 2: re-hire is scoped to the HR context's
        // tenant, and a lookup that forgot to say so would revive (or refuse on) another company's row.
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());
        UserAccount elsewhere = closedRowInTenant(9L, PERSON, OLD_EMAIL, MOBILE);
        doReturn(List.of(elsewhere)).when(accountService)
                .searchList(argThat((Filters f) -> !namesThisTenant(f)));

        inThisTenant(() -> assertThatCode(() -> accountService.registerInvitedUser(OLD_EMAIL, MOBILE, "Ada L"))
                .doesNotThrowAnyException());

        // A fresh person is minted here, exactly as for any unknown contact.
        verify(accountService, never()).reviveMembership(any(), any(), any());
        verify(accountService).createOne(any(UserAccount.class));
        verify(profileService).registerUserProfile(anyLong(), any(UserProfileDTO.class));
    }

    @Test
    void aClosedRowWithoutAPersonDoesNotAnchor() {
        // Nobody to revive: the row is then just another holder of the address, and it refuses like one.
        when(identityService.findByLoginIdentifier(anyString())).thenReturn(Optional.empty());
        UserAccount orphan = closedRowInTenant(THIS_TENANT, null, OLD_EMAIL, null);
        doReturn(List.of(orphan)).when(accountService).searchList(any(Filters.class));

        inThisTenant(() -> assertThatThrownBy(() -> accountService.registerInvitedUser(OLD_EMAIL, null, "Ada L"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already exists"));

        verify(accountService, never()).reviveMembership(any(), any(), any());
        verify(accountService, never()).createOne(any(UserAccount.class));
    }

    @Test
    void somebodyElsesRowHoldingTheContactIsStillADuplicate() {
        // The exclusion is for the leaver's OWN closed row only — a colleague's live account holding
        // the address is exactly what uk_user_account_tenant_email refuses.
        personHasMembershipHere(AccountStatus.DEACTIVATED);
        UserAccount colleague = new UserAccount();
        colleague.setId(555L);
        colleague.setTenantId(THIS_TENANT);
        colleague.setEmail(NEW_EMAIL);
        doReturn(List.of(colleague)).when(accountService).searchList(any(Filters.class));

        inThisTenant(() -> assertThatThrownBy(() -> accountService.registerInvitedUser(NEW_EMAIL, MOBILE, "Ada L"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("Email already exists"));

        verify(accountService, never()).reviveMembership(any(), any(), any());
    }
}
