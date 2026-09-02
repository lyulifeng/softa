package io.softa.starter.user.dto;

import io.softa.starter.user.util.LoginIdentifiers;

/**
 * The PLAINTEXT contacts an invitation names. Server-internal — these values must never reach a
 * response body, or the masking on the join screens would be pointless.
 *
 * @param email  work email the invitation was addressed to, or null
 * @param mobile work mobile the invitation was addressed to, or null
 */
public record JoinContacts(String email, String mobile) {

    /**
     * Whether {@code address} is one of the two this invitation names. Null never matches.
     *
     * <p>Both sides go through {@link LoginIdentifiers#normalize}. The invitation carries the work
     * contact as HR typed it, while what is compared against it is a login identifier that was
     * STORED canonical (createPersonForJoin seeds it from this very address, normalised). A raw
     * comparison refused a first-time invitee whose contact HR typed with a stray space — the
     * seeded identifier {@code ada@acme.com} never equalled {@code " Ada@Acme.com "}, so the
     * person passed the code and was then told the link was not theirs.
     */
    public boolean includes(String address) {
        String candidate = LoginIdentifiers.normalize(address);
        if (candidate == null) {
            return false;
        }
        return candidate.equals(LoginIdentifiers.normalize(email))
                || candidate.equals(LoginIdentifiers.normalize(mobile));
    }
}
