package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.BusinessException;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.enums.AccountStatus;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The account status axis PENDING → INVITED → ACTIVE, which is what makes
 * "created" distinguishable from "contacted" in the account list.
 *
 * <p>Exercises the transition {@code invite()} applies, which carries the rules:
 *
 * <ul>
 *   <li>PENDING (created, never contacted) → INVITED when the invitation goes out;</li>
 *   <li>INVITED stays INVITED and reports "no change" — re-inviting is the normal
 *       remedy for a lost or expired mail, and must not rewrite the row;</li>
 *   <li>an account that already has a password is NEVER demoted — re-inviting a
 *       working ACTIVE account only re-sends a link.</li>
 * </ul>
 */
class InvitationStatusAxisTest {

    private static UserAccount account(AccountStatus status) {
        UserAccount account = new UserAccount();
        account.setId(7L);
        account.setEmail("employee@example.com");
        account.setStatus(status);
        return account;
    }

    @Test
    void pendingAccount_isFlippedToInvited_andReportsAWrite() {
        UserAccount pending = account(AccountStatus.PENDING);

        boolean changed = UserInvitationServiceImpl.applyInviteTransition(pending, false);

        assertThat(changed).isTrue();
        assertThat(pending.getStatus()).isEqualTo(AccountStatus.INVITED);
    }

    @Test
    void alreadyInvited_staysInvited_andReportsNoWrite() {
        UserAccount invited = account(AccountStatus.INVITED);

        boolean changed = UserInvitationServiceImpl.applyInviteTransition(invited, false);

        // Re-inviting re-sends the link; the row is already right, so no write.
        assertThat(changed).isFalse();
        assertThat(invited.getStatus()).isEqualTo(AccountStatus.INVITED);
    }

    @Test
    void activeAccountWithPassword_isNeverDemoted() {
        UserAccount active = account(AccountStatus.ACTIVE);

        boolean changed = UserInvitationServiceImpl.applyInviteTransition(active, true);

        assertThat(changed).isFalse();
        assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void lockedAccountWithPassword_isNeverDemoted() {
        // The guard is "has a password", not "is ACTIVE" — a locked or frozen
        // account must not be silently reopened into the invitation flow either.
        UserAccount locked = account(AccountStatus.LOCKED);

        boolean changed = UserInvitationServiceImpl.applyInviteTransition(locked, true);

        assertThat(changed).isFalse();
        assertThat(locked.getStatus()).isEqualTo(AccountStatus.LOCKED);
    }

    // ── revoke:Invited → Pending,旧链作废(PRD D3 / W4)──────────────

    @Test
    void revokingAnInvitation_returnsTheAccountToPending() {
        UserAccount invited = account(AccountStatus.INVITED);

        boolean changed = UserInvitationServiceImpl.applyRevokeTransition(invited, false);

        assertThat(changed).isTrue();
        assertThat(invited.getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void revokingAnAlreadyJoinedAccount_isRefused() {
        // Revoking would strand a working account in a pre-activation state that nothing
        // can move forward. Ending an existing membership is off-boarding, not revocation.
        UserAccount active = account(AccountStatus.ACTIVE);

        assertThatThrownBy(() -> UserInvitationServiceImpl.applyRevokeTransition(active, true))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("already joined");
        assertThat(active.getStatus()).isEqualTo(AccountStatus.ACTIVE);
    }

    @Test
    void revokingAPendingAccount_reportsNoStatusChange() {
        // Idempotent: nothing to move. The caller still invalidates any stray token —
        // that step is deliberately NOT gated on this returning true.
        UserAccount pending = account(AccountStatus.PENDING);

        boolean changed = UserInvitationServiceImpl.applyRevokeTransition(pending, false);

        assertThat(changed).isFalse();
        assertThat(pending.getStatus()).isEqualTo(AccountStatus.PENDING);
    }

    @Test
    void inviteThenRevoke_roundTrips() {
        // The two transitions are inverses on the pre-activation axis, which is what makes
        // "invite again after revoking" (D3) work without a special case.
        UserAccount account = account(AccountStatus.PENDING);

        UserInvitationServiceImpl.applyInviteTransition(account, false);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INVITED);

        UserInvitationServiceImpl.applyRevokeTransition(account, false);
        assertThat(account.getStatus()).isEqualTo(AccountStatus.PENDING);

        assertThat(UserInvitationServiceImpl.applyInviteTransition(account, false)).isTrue();
        assertThat(account.getStatus()).isEqualTo(AccountStatus.INVITED);
    }

    @Test
    void deactivatedIsAvailableForOffboarding() {
        // Off-boarding's terminal state (PRD §2.1). Distinct from Frozen: Frozen is an
        // admin hold on a current member, Deactivated ends the membership.
        assertThat(AccountStatus.DEACTIVATED.getStatus()).isEqualTo("Deactivated");
        assertThat(AccountStatus.DEACTIVATED).isNotEqualTo(AccountStatus.FROZEN);
    }

    @Test
    void pendingIsDistinctFromInvited_andSerialisesAsPending() {
        // The two states must not collapse: the account list answers "has this
        // person been contacted?" purely from this distinction, and the code is
        // what the FE filters on.
        assertThat(AccountStatus.PENDING).isNotEqualTo(AccountStatus.INVITED);
        assertThat(AccountStatus.PENDING.getStatus()).isEqualTo("Pending");
    }
}
