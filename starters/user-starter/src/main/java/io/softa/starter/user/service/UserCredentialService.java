package io.softa.starter.user.service;

import java.util.Optional;

import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;

/**
 * The single seam for reading and writing credentials, which live on the person's {@link UserIdentity}.
 *
 * <p>Exists so that "where is the password" is answered in exactly one place. Before this, four
 * services hashed and compared passwords themselves; moving the storage would have meant finding
 * every one of them, and missing one would have left an account that authenticates against a
 * stale hash. Because they all go through here, splitting the credentials out into {@link UserIdentity}
 * touched this class and nothing they call.
 */
public interface UserCredentialService {

    /**
     * The credentials behind a membership, resolved from {@code account.profileId}.
     *
     * <p>Takes the account rather than its id on purpose. Looking it up here would mean depending
     * on {@link UserAccountService}, which depends on this service for every password it writes —
     * a bean cycle Spring refuses outright. Every caller already holds the account anyway.
     */
    UserIdentity requireIdentity(UserAccount account);

    /**
     * ⚠️ NO CALLER THIS RELEASE — deliberately. Login still resolves the account by its email;
     * this (and the loginEmail/loginMobile columns it queries, seeded but unread) is the seam the
     * "resolve people by identifier" release will switch to. It ships now so that release needs no
     * data backfill — the expensive half of that change is the data, and it is being populated
     * from day one. Kept rather than deferred on purpose; do not delete as "dead code".
     */
    Optional<UserIdentity> findByLoginIdentifier(String identifier);

    /** False when the person has no stored password — that is not the same as "any password fits". */
    boolean matchesPassword(UserIdentity identity, String rawPassword);

    /** Hashes and stores a new password on the person's identity, with a fresh salt. */
    void setPassword(Long identityId, String rawPassword);
}
