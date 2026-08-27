package io.softa.starter.user.dto;

/**
 * The PLAINTEXT contacts an invitation names. Server-internal — these values must never reach a
 * response body, or the masking on the join screens would be pointless.
 *
 * @param email  work email the invitation was addressed to, or null
 * @param mobile work mobile the invitation was addressed to, or null
 */
public record JoinContacts(String email, String mobile) {

    /** Whether {@code address} is one of the two this invitation names. Null never matches. */
    public boolean includes(String address) {
        if (address == null) {
            return false;
        }
        return address.equalsIgnoreCase(email) || address.equalsIgnoreCase(mobile);
    }
}
