package io.softa.starter.user.util;

import java.util.Locale;
import java.util.regex.Pattern;

import org.apache.commons.lang3.StringUtils;

/**
 * The one spelling a login identifier has: trimmed, lowercased, and for a mobile number stripped of
 * the separators people type into it.
 *
 * <p>Applied wherever an identifier is STORED, LOOKED UP or HASHED, so that the three can never
 * disagree about which string names a person. They did disagree: the unknown-identifier counter
 * hashed the trimmed, lowercased form while the identity lookup queried the raw one, so an
 * identifier typed with a leading space always resolved to nobody. Seven guesses at {@code " x"}
 * then one at {@code "x"}: an unknown {@code x} counted to eight and showed the countdown, a known
 * one landed on its own fresh counter and did not — an existence oracle that no collation setting
 * could close, because the space never reached the database.
 *
 * <p>Lowercasing is what makes two spellings of one mailbox one identifier. For a mobile the
 * equivalent is {@link #collapseMobile}: {@code +65 9123-4567} and {@code +6591234567} are one
 * number, and a code sent to one had to resolve the identity stored under the other — a person
 * whose identifier HR seeded with the spaces the form allowed could otherwise never sign in by
 * mobile, and two rows holding one number in two spellings read as unshared to the shared-contact
 * guard, which exists precisely to catch a number held twice. Work contacts ({@code UserAccount.email}
 * / {@code mobile}) keep HR's case and are NOT lowercased here — but they do go through
 * {@code collapseMobile}, so the equality queries on those columns find one number however it was
 * typed. This lives in {@code util} because the invitation's {@code JoinContacts} in {@code dto}
 * compares against a stored identifier and must apply the identical rule, not a copy.
 */
public final class LoginIdentifiers {

    /**
     * What a phone number looks like once trimmed: a dial code, or nothing but digits and the
     * separators people put between them (space, hyphen, non-breaking space). An email never
     * matches, so its spaces — which would make it a different mailbox — are left alone.
     */
    private static final Pattern MOBILE_SHAPE = Pattern.compile("\\+.*|[0-9 \\-\\u00A0]+");
    private static final Pattern MOBILE_SEPARATORS = Pattern.compile("[ \\-\\u00A0]");

    private LoginIdentifiers() {
    }

    /** The canonical form, or null for a blank input. */
    public static String normalize(String identifier) {
        String value = StringUtils.trimToNull(identifier);
        return value == null ? null : collapseMobile(value).toLowerCase(Locale.ROOT);
    }

    /**
     * A phone-shaped value with its internal spaces, hyphens and non-breaking spaces removed; any
     * other value (an email, a blank) unchanged. Case is never touched here, so the work-contact
     * columns can apply it without taking on the lowercasing that belongs to login identifiers only.
     */
    public static String collapseMobile(String value) {
        if (value == null || !MOBILE_SHAPE.matcher(value).matches()) {
            return value;
        }
        return MOBILE_SEPARATORS.matcher(value).replaceAll("");
    }
}
