package io.softa.starter.user.service.impl;

import java.util.Locale;

import org.apache.commons.lang3.StringUtils;

/**
 * The one spelling a login identifier has: trimmed, lowercased.
 *
 * <p>Applied wherever an identifier is STORED, LOOKED UP or HASHED, so that the three can never
 * disagree about which string names a person. They did disagree: the unknown-identifier counter
 * hashed the trimmed, lowercased form while the identity lookup queried the raw one, so an
 * identifier typed with a leading space always resolved to nobody. Seven guesses at {@code " x"}
 * then one at {@code "x"}: an unknown {@code x} counted to eight and showed the countdown, a known
 * one landed on its own fresh counter and did not — an existence oracle that no collation setting
 * could close, because the space never reached the database.
 *
 * <p>Lowercasing is what makes two spellings of one mailbox one identifier; for a dial-code mobile
 * it is a no-op. Work contacts ({@code UserAccount.email} / {@code mobile}) are display values and
 * are deliberately NOT passed through here — only login identifiers, and the strings compared
 * against them.
 */
final class LoginIdentifiers {

    private LoginIdentifiers() {
    }

    /** The canonical form, or null for a blank input. */
    static String normalize(String identifier) {
        String value = StringUtils.trimToNull(identifier);
        return value == null ? null : value.toLowerCase(Locale.ROOT);
    }
}
