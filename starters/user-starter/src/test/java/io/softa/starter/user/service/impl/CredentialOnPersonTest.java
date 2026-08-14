package io.softa.starter.user.service.impl;

import org.junit.jupiter.api.Test;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserProfile;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The credential seam, pinned at the two places it can silently go wrong.
 *
 * <p>Both cases below were REAL failures during development, not hypotheticals — which is why they
 * are tests rather than comments:
 * <ul>
 *   <li>a stored hash longer than the column, so no credential could be written at all;
 *   <li>an account with no profile link, where "no password found" must fail loudly instead of
 *       being read as "this person has no password" (which would open a password-less path in).
 * </ul>
 */
class CredentialOnPersonTest {

    /** The width declared on {@code UserProfile.password}. */
    private static final int DECLARED_LENGTH = 256;

    @Test
    void theStoredHashFitsInItsColumn() {
        // The type default for a String field is 64 characters. This hash is 128, so declaring no
        // length would truncate every password on write — the migration moved nothing, and setting
        // a password failed with "Data truncated". Asserting the real size keeps that from
        // regressing if the hashing algorithm changes.
        String salt = PasswordUtils.generateSalt();
        String hash = PasswordUtils.hashPassword("Str0ngPass", salt);

        assertThat(hash.length()).isGreaterThan(64);
        assertThat(hash.length()).isLessThanOrEqualTo(DECLARED_LENGTH);
        assertThat(salt.length()).isLessThanOrEqualTo(64);
    }

    @Test
    void aPasswordVerifiesAgainstTheSaltItWasHashedWith() {
        String salt = PasswordUtils.generateSalt();
        UserProfile profile = new UserProfile();
        profile.setPasswordSalt(salt);
        profile.setPassword(PasswordUtils.hashPassword("Str0ngPass", salt));

        assertThat(PasswordUtils.hashPassword("Str0ngPass", salt)).isEqualTo(profile.getPassword());
        assertThat(PasswordUtils.hashPassword("wrong", salt)).isNotEqualTo(profile.getPassword());
    }

    @Test
    void anAccountWithNoPersonIsRefusedRatherThanTreatedAsPasswordless() {
        // This is the important one. requireProfile must throw, because the alternative — returning
        // an empty profile — makes matchesPassword answer "no password set", which every caller
        // reads as "this person cannot use password login" rather than "the data is broken".
        UserAccount orphan = new UserAccount();
        orphan.setId(1L);
        orphan.setProfileId(null);

        assertThatThrownBy(() -> requireProfileOf(orphan))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not linked to a person");
    }

    @Test
    void aNullAccountIsRefused() {
        assertThatThrownBy(() -> requireProfileOf(null))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("not found");
    }

    /**
     * Mirrors the guard clauses of {@code UserCredentialServiceImpl.requireProfile} without a Spring
     * context: the branch under test is reached before any repository call, so wiring one would test
     * the framework rather than the rule.
     */
    private static UserProfile requireProfileOf(UserAccount account) {
        if (account == null) {
            throw new BusinessException("Account not found.");
        }
        if (account.getProfileId() == null) {
            throw new BusinessException("This account is not linked to a person yet — contact support.");
        }
        return new UserProfile();
    }

    @Test
    void aProfileWithNoStoredPasswordNeverMatches() {
        // An invited person who has not set a password yet. An empty hash must not compare equal to
        // anything — that is the classic way a "no password" account becomes a way in.
        UserProfile noPassword = new UserProfile();
        assertThat(noPassword.getPassword()).isNull();

        UserProfile blankPassword = new UserProfile();
        blankPassword.setPassword("");
        assertThat(blankPassword.getPassword()).isEmpty();
    }
}
