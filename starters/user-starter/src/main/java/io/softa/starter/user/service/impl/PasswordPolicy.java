package io.softa.starter.user.service.impl;

import java.util.ArrayList;
import java.util.List;

import org.apache.commons.lang3.StringUtils;

import io.softa.framework.base.exception.BusinessException;

/**
 * The password rules (PRD D4), in one place so the server and the on-screen checklist cannot drift.
 *
 * <ol>
 *   <li>8–32 characters;</li>
 *   <li>contains an upper-case AND a lower-case letter;</li>
 *   <li>contains a digit;</li>
 *   <li>no spaces, and does not contain the last 4 digits of the person's mobile or the local part
 *       of their email.</li>
 * </ol>
 *
 * <p>Rule 4 is the one worth explaining: length and character-class rules only bound the search
 * space, while "not derived from your own contact details" removes the guesses an attacker who
 * knows the target would try FIRST. Both halves are needed, which is why it is checked against the
 * caller's actual identifiers rather than a generic dictionary.
 *
 * <p>Special characters are allowed but not required — requiring them pushes people towards
 * predictable substitutions, and PRD D4 chose not to.
 */
public final class PasswordPolicy {

    private static final int MIN_LENGTH = 8;
    private static final int MAX_LENGTH = 32;
    /** How much of a mobile number is considered guessable. PRD D4 says the last 4 digits. */
    private static final int MOBILE_TAIL = 4;
    /** Below this length an email local part is too short to be a meaningful constraint. */
    private static final int MIN_EMAIL_LOCAL_PART = 3;

    private PasswordPolicy() {
    }

    /**
     * Validate a candidate password, throwing with EVERY unmet rule rather than the first.
     *
     * <p>Reporting one at a time turns setting a password into a guessing game — the screen shows a
     * four-item checklist, so the server has to be able to say which items failed.
     *
     * @param password the candidate
     * @param mobile   the person's mobile (may be null); its last 4 digits are disallowed
     * @param email    the person's email (may be null); its local part is disallowed
     * @throws BusinessException listing every rule the password fails
     */
    public static void validate(String password, String mobile, String email) {
        List<String> failures = check(password, mobile, email);
        if (!failures.isEmpty()) {
            throw new BusinessException("Password does not meet the requirements: "
                    + String.join("; ", failures));
        }
    }

    /** The unmet rules, in checklist order; empty when the password is acceptable. */
    public static List<String> check(String password, String mobile, String email) {
        List<String> failures = new ArrayList<>();
        if (password == null) {
            failures.add("8–32 characters");
            return failures;
        }
        if (password.length() < MIN_LENGTH || password.length() > MAX_LENGTH) {
            failures.add("8–32 characters");
        }
        boolean upper = password.chars().anyMatch(Character::isUpperCase);
        boolean lower = password.chars().anyMatch(Character::isLowerCase);
        if (!upper || !lower) {
            failures.add("contains uppercase and lowercase letters");
        }
        if (password.chars().noneMatch(Character::isDigit)) {
            failures.add("contains numbers");
        }
        if (containsWhitespace(password) || derivedFromContact(password, mobile, email)) {
            // One checklist line for both, matching the screen: "No spaces or parts of your
            // phone / email". Splitting it here would leave the UI unable to map the message.
            failures.add("no spaces or parts of your phone / email");
        }
        return failures;
    }

    private static boolean containsWhitespace(String password) {
        return password.chars().anyMatch(Character::isWhitespace);
    }

    /**
     * Whether the password embeds the guessable part of the person's own contact details.
     *
     * <p>Compared case-insensitively: an attacker trying the email local part will try its
     * capitalisations too, so matching only the exact case would be a rule in name alone.
     */
    private static boolean derivedFromContact(String password, String mobile, String email) {
        String candidate = password.toLowerCase();
        if (StringUtils.isNotBlank(mobile)) {
            String digits = mobile.replaceAll("\\D", "");
            if (digits.length() >= MOBILE_TAIL
                    && candidate.contains(digits.substring(digits.length() - MOBILE_TAIL))) {
                return true;
            }
        }
        if (StringUtils.isNotBlank(email)) {
            int at = email.indexOf('@');
            String localPart = (at > 0 ? email.substring(0, at) : email).toLowerCase();
            // Very short local parts ("hr@", "it@") would reject far too much to be useful.
            if (localPart.length() >= MIN_EMAIL_LOCAL_PART && candidate.contains(localPart)) {
                return true;
            }
        }
        return false;
    }
}
