package io.softa.starter.user.service;

import java.util.Optional;

import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserProfile;

/**
 * The single seam for reading and writing credentials, which now live on the PERSON.
 *
 * <p>Exists so that "where is the password" is answered in exactly one place. Before this, four
 * services hashed and compared passwords themselves; moving the storage would have meant finding
 * every one of them, and missing one would have left an account that authenticates against a
 * stale hash.
 */
public interface UserCredentialService {

    /**
     * The person behind a membership.
     *
     * <p>Takes the account rather than its id on purpose. Looking it up here would mean depending
     * on {@link UserAccountService}, which depends on this service for every password it writes —
     * a bean cycle Spring refuses outright. Every caller already holds the account anyway.
     */
    UserProfile requireProfile(UserAccount account);

    /**
     * Finds a person by an email OR a dial-code mobile.
     *
     * <p>Both are tried rather than guessing by shape ("@" or "+"): a caller that guesses wrong
     * would report "no such account" for an account that exists.
     */
    Optional<UserProfile> findByLoginIdentifier(String identifier);

    /** False when the person has no stored password — that is not the same as "any password fits". */
    boolean matchesPassword(UserProfile profile, String rawPassword);

    /** Hashes and stores a new password on the person, with a fresh salt. */
    void setPassword(Long profileId, String rawPassword);
}
