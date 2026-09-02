package io.softa.starter.user.service.impl;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.HexFormat;
import java.util.Objects;
import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserIdentityService;

/**
 * {@link UserIdentityService} — the person's credentials, stored and verified here.
 *
 * <p>{@code @CrossTenant} on the lookups: a login identifier is global by definition, and the caller
 * has no tenant context yet — that is decided AFTER authenticating. Scoping these to a tenant would
 * make "who is this?" answerable only once you already knew. The annotation is entered through the
 * proxy on the public method, so the nested query below is covered by it too.
 *
 * <p>Injects only the cache, for the password-failure counter. The persistence comes from
 * {@code EntityServiceImpl}, and {@code requireIdentity} takes the account object rather than
 * looking it up — see the interface for why that matters (a bean cycle with
 * {@code UserAccountService}).
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserIdentityServiceImpl extends EntityServiceImpl<UserIdentity, Long>
        implements UserIdentityService {

    /** Consecutive wrong passwords that lock the password path (PRD D5). */
    /** How long it stays locked. */
    private static final int LOCK_MINUTES = 30;

    private final CacheService cacheService;

    private static String failureKey(Long identityId) {
        return "login:pwd-failures:" + identityId;
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public UserIdentity requireIdentity(UserAccount account) {
        if (account == null) {
            throw new BusinessException("Account not found.");
        }
        Long profileId = account.getProfileId();
        if (profileId == null) {
            // After the migration this is a data fault, not a normal state. Treating it as "no
            // password" would let a password-less path through, so refuse loudly instead.
            throw new BusinessException("This account is not linked to a person yet — contact support.");
        }
        return this.findByProfile(profileId)
                .orElseThrow(() -> new BusinessException("Credentials not found for this account."));
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public Optional<UserIdentity> findByProfile(Long profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return this.searchOne(new Filters().eq(UserIdentity::getProfileId, profileId));
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public Optional<UserIdentity> findByLoginIdentifier(String identifier) {
        // Same spelling the seeding wrote and the unknown counter hashes — see LoginIdentifiers.
        identifier = LoginIdentifiers.normalize(identifier);
        if (identifier == null) {
            return Optional.empty();
        }
        // Both are tried rather than guessing by shape ("@" or "+"): a caller that guessed wrong
        // would report "no such account" for an account that exists.
        //
        // Both are ALWAYS run, even once the email query has hit. Returning early on a hit makes a
        // known email cost one query and an unknown one two — a difference an observer with a
        // stopwatch and two identifiers can read at the anonymous login form, however identical the
        // refusals are worded. Paying the second query on a hit is what keeps the timing flat, and
        // it is done here so every caller gets it rather than each remembering to.
        Optional<UserIdentity> byEmail = this.searchOneIdentifier(
                new Filters().eq(UserIdentity::getLoginEmail, identifier), identifier);
        Optional<UserIdentity> byMobile = this.searchOneIdentifier(
                new Filters().eq(UserIdentity::getLoginMobile, identifier), identifier);
        return byEmail.isPresent() ? byEmail : byMobile;
    }

    /**
     * One identity for this identifier, refusing loudly when several rows claim it.
     *
     * <p>The unique indexes on the identifier columns (see {@link UserIdentity}) make more than one
     * row impossible at the database level, so this is defence-in-depth rather than the primary
     * guard. If it ever did happen — a partially-applied migration, a manual write — the raw
     * {@code searchOne} throws an {@code IllegalArgumentException} carrying the filter, which at an
     * anonymous login endpoint would surface as a server error quoting somebody's address.
     * Translated here to a refusal instead: ambiguous is not "pick one", because picking would mean
     * signing someone in as a person who merely shares their phone number.
     */
    private Optional<UserIdentity> searchOneIdentifier(Filters filters, String identifier) {
        try {
            return this.searchOne(filters);
        } catch (IllegalArgumentException e) {
            log.error("Login identifier is not unique — several people claim it. Refusing to guess.");
            throw new BusinessException("This contact is shared by more than one account. "
                    + "Please contact your administrator.");
        }
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public boolean isIdentifierClaimable(String identifier, Long forProfileId) {
        identifier = LoginIdentifiers.normalize(identifier);
        if (identifier == null) {
            return false;
        }
        boolean isEmail = identifier.contains("@");
        Filters filters = isEmail
                ? new Filters().eq(UserIdentity::getLoginEmail, identifier)
                : new Filters().eq(UserIdentity::getLoginMobile, identifier);
        // searchList, not searchOne: the point is to COUNT claimants (0 = free, 1 = held, and if a
        // migration ever left more than one, searchOne would throw rather than count them).
        return this.searchList(filters).stream()
                .allMatch(other -> Objects.equals(other.getProfileId(), forProfileId));
    }


    @Override
    public boolean matchesPassword(UserIdentity identity, String rawPassword) {
        if (identity == null || StringUtils.isBlank(identity.getPassword())
                || StringUtils.isBlank(rawPassword)) {
            // No stored password = password login is not available for this person (they arrived by
            // invitation and have not set one). Returning true for an empty hash is the classic way
            // that rule gets broken.
            return false;
        }
        String hashed = PasswordUtils.hashPassword(rawPassword, identity.getPasswordSalt());
        return hashed.equals(identity.getPassword());
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public void setPassword(UserAccount account, String rawPassword) {
        this.setPassword(requireIdentity(account).getId(), rawPassword);
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public void setPassword(Long identityId, String rawPassword) {
        UserIdentity identity = this.getById(identityId)
                .orElseThrow(() -> new BusinessException("Credentials not found."));
        // Enforced HERE rather than at each call site: invitation-accept, forced set-password,
        // self-service change and admin reset all land in this method, and a rule that every
        // caller must remember to apply is a rule that one of them eventually will not.
        // Checked against the person's OWN identifiers — that is what makes "not derived from
        // your contact details" mean anything (PRD D4).
        PasswordPolicy.validate(rawPassword, identity.getLoginMobile(), identity.getLoginEmail());
        String salt = PasswordUtils.generateSalt();
        identity.setPasswordSalt(salt);
        identity.setPassword(PasswordUtils.hashPassword(rawPassword, salt));
        // A new password ends the window AND the lock: the guesses were against the old password,
        // and leaving the lock standing would mean someone who reset theirs still cannot use it.
        // updateOne(entity, false) because clearing the lock means writing a null, which the
        // default overload drops.
        identity.setPasswordLockedUntil(null);
        this.updateOne(identity, false);
        this.clearPasswordFailures(identityId);
    }

    @Override
    public boolean isPasswordLocked(UserIdentity identity) {
        return identity != null && identity.getPasswordLockedUntil() != null
                && identity.getPasswordLockedUntil().isAfter(LocalDateTime.now());
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public long recordPasswordFailure(UserIdentity identity) {
        if (identity == null || identity.getId() == null) {
            return 0;
        }
        Long failures = cacheService.increment(failureKey(identity.getId()), LOCK_MINUTES * 60L);
        if (failures == null) {
            return 0;
        }
        if (failures < FAILURES_BEFORE_LOCK) {
            return failures;
        }
        identity.setPasswordLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        this.updateOne(identity);
        // Counter cleared with the lock, so the next window starts fresh when it expires —
        // otherwise one wrong password after unlocking would immediately re-lock.
        this.clearPasswordFailures(identity.getId());
        log.warn("Password login locked for {} minutes after {} consecutive failures (identity {}).",
                LOCK_MINUTES, failures, identity.getId());
        return failures;
    }

    /*
     * What remains distinguishable BY DESIGN between a known and an unknown identifier, once the
     * messages, counts, clocks and spelling agree:
     *
     *  - The real counter is per PERSON, this one per STRING. A person's email and mobile lock
     *    together (ten wrong passwords split across them lock both), while two unknown strings never
     *    share a count. An observer who can pair two identifiers and spend the window on them can
     *    learn that they belong to one real person. Accepted: keying the real counter per string
     *    would hand a person with two identifiers twenty guesses, and the pairing already requires
     *    knowing both of someone's contacts.
     *  - The storage medium differs. The real lock is persisted on the identity row so a cache flush
     *    cannot unlock it; the unknown lock lives only in the cache. A flush therefore frees unknown
     *    identifiers and not real ones — observable only by whoever can cause the flush.
     *  - Timing. Both branches run the same two identifier queries, but the real branch then hashes
     *    a password and writes a row on the tenth failure, the unknown one increments a cache key.
     *    Sub-millisecond, and below what the network jitter of an anonymous form exposes; not
     *    equalised further.
     */
    @Override
    public long recordUnknownIdentifierFailure(String identifier) {
        String value = LoginIdentifiers.normalize(identifier);
        if (value == null) {
            return 0;
        }
        // Same window as the real counter, so the two branches age identically.
        String digest = unknownDigest(value);
        Long failures = cacheService.increment(unknownFailureKey(digest), LOCK_MINUTES * 60L);
        if (failures == null) {
            return 0;
        }
        if (failures >= FAILURES_BEFORE_LOCK) {
            // Mirror recordPasswordFailure exactly: a lock that runs LOCK_MINUTES from THIS failure,
            // and a counter reset so the next window starts fresh. Letting the counter stand in for
            // the lock does not work — its TTL runs from the first failure, the real lock from the
            // tenth, and between those two expiries the branches give different answers.
            cacheService.save(unknownLockKey(digest), "1", LOCK_MINUTES * 60);
            cacheService.clear(unknownFailureKey(digest));
        }
        return failures;
    }

    @Override
    public boolean isUnknownIdentifierLocked(String identifier) {
        String value = LoginIdentifiers.normalize(identifier);
        return value != null && cacheService.hasKey(unknownLockKey(unknownDigest(value)));
    }

    private static String unknownFailureKey(String digest) {
        return "login:pwd-failures:unknown:" + digest;
    }

    private static String unknownLockKey(String digest) {
        return "login:pwd-lock:unknown:" + digest;
    }

    private static String unknownDigest(String identifier) {
        // Hashed rather than stored: the key would otherwise be a list of every identifier ever
        // guessed at the login form. The input is already in LoginIdentifiers' canonical form and
        // is hashed as-is: any further transformation here is one the known branch's lookup would
        // not make, and a difference between the two is the oracle this counter exists to close.
        byte[] digest;
        try {
            digest = MessageDigest.getInstance("SHA-256")
                    .digest(identifier.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is a mandatory JCA algorithm.", e);
        }
        return HexFormat.of().formatHex(digest);
    }

    @Override
    public void clearPasswordFailures(Long identityId) {
        if (identityId != null) {
            cacheService.clear(failureKey(identityId));
        }
    }
}
