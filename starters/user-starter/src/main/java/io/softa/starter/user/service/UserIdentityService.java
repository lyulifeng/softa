package io.softa.starter.user.service;

import java.util.Optional;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;

/**
 * The person's credentials: persistence AND the rules for reading and writing them.
 *
 * <p>One service, not two. "Where is the password" has to have exactly one answer — before the
 * credentials had a model of their own, that meant a separate seam ({@code UserCredentialService})
 * sitting in front of whichever model happened to hold them, because four services were hashing and
 * comparing passwords themselves and moving the storage meant finding every one of them. Now that
 * the credentials ARE a model, this service is that seam, and a second one in front of it would only
 * add a hop.
 *
 * <p>Deliberately exposes no unguarded lookup. Fetching an identity by profile id or by login
 * identifier is possible, but only through the methods below: a bare finder returning an empty
 * Optional invites the caller to read "no credentials" as "this person has no password", which is
 * how a password-less path gets opened. {@link #requireIdentity} refuses loudly instead.
 */
public interface UserIdentityService extends EntityService<UserIdentity, Long> {

    /**
     * The credentials of the person a membership belongs to, resolved from {@code account.profileId}.
     *
     * <p>Takes the account rather than its id on purpose. Looking it up here would mean depending on
     * {@link UserAccountService}, which depends on this service for every password it writes — a bean
     * cycle Spring refuses outright. Every caller already holds the account anyway.
     */
    UserIdentity requireIdentity(UserAccount account);

    /**
     * Finds a person's credentials by an email OR a dial-code mobile.
     *
     * <p>⚠️ NO CALLER THIS RELEASE — deliberately. Login still resolves the account by its email;
     * this (and the loginEmail/loginMobile columns it queries, seeded but unread) is the seam the
     * "resolve people by identifier" release will switch to. It ships now so that release needs no
     * data backfill — the expensive half of that change is the data, and it is being populated from
     * day one. Kept rather than deferred on purpose; do not delete as "dead code".
     */
    Optional<UserIdentity> findByLoginIdentifier(String identifier);

    /** False when the person has no stored password — that is not the same as "any password fits". */
    boolean matchesPassword(UserIdentity identity, String rawPassword);

    /** Hashes and stores a new password on the person's credentials, with a fresh salt. */
    void setPassword(Long identityId, String rawPassword);

    /**
     * Same, for the person behind a membership — the common case.
     *
     * <p>Exists because "resolve the identity, take its id, set the password" was written out at
     * every call site, and nesting the three reads badly enough to hide the interesting part: which
     * account the password lands on.
     */
    void setPassword(UserAccount account, String rawPassword);
}
