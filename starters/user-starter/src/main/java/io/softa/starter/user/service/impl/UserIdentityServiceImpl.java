package io.softa.starter.user.service.impl;

import java.time.LocalDateTime;
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
    private static final int FAILURES_BEFORE_LOCK = 10;
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
        if (StringUtils.isBlank(identifier)) {
            return Optional.empty();
        }
        // Both are tried rather than guessing by shape ("@" or "+"): a caller that guessed wrong
        // would report "no such account" for an account that exists.
        Optional<UserIdentity> byEmail = this.searchOneIdentifier(
                new Filters().eq(UserIdentity::getLoginEmail, identifier), identifier);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return this.searchOneIdentifier(
                new Filters().eq(UserIdentity::getLoginMobile, identifier), identifier);
    }

    /**
     * One identity for this identifier, refusing loudly when several rows claim it.
     *
     * <p>The identifier columns carry no unique index yet (see {@link UserIdentity}), so the
     * database cannot rule this out. The raw {@code searchOne} does throw on more than one row,
     * but as an {@code IllegalArgumentException} carrying the filter — which would surface at an
     * anonymous login endpoint as a server error quoting somebody's address. Translated here so
     * the caller reports a refusal instead: ambiguous is not "pick one", because picking would
     * mean signing someone in as a person who merely shares their phone number.
     */
    private Optional<UserIdentity> searchOneIdentifier(Filters filters, String identifier) {
        try {
            return this.searchOne(filters);
        } catch (IllegalArgumentException e) {
            log.error("Login identifier is not unique — several people claim it. Refusing to guess.");
            throw new BusinessException("This contact is shared by more than one account. "
                    + "Please contact your HR.");
        }
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public void adoptIdentifier(UserIdentity identity, String identifier) {
        if (identity == null || StringUtils.isBlank(identifier)) {
            return;
        }
        boolean isEmail = identifier.contains("@");
        if (isEmail ? StringUtils.isNotBlank(identity.getLoginEmail())
                : StringUtils.isNotBlank(identity.getLoginMobile())) {
            return;
        }
        if (isEmail) {
            identity.setLoginEmail(identifier);
        } else {
            identity.setLoginMobile(identifier);
        }
        this.updateOne(identity);
        log.info("Backfilled the login {} of identity {} from the account's work contact.",
                isEmail ? "email" : "mobile", identity.getId());
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
        this.updateOne(identity);
        // A new password ends the window: the guesses were against the old one.
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
    public void recordPasswordFailure(UserIdentity identity) {
        if (identity == null || identity.getId() == null) {
            return;
        }
        Long failures = cacheService.increment(failureKey(identity.getId()), LOCK_MINUTES * 60L);
        if (failures == null || failures < FAILURES_BEFORE_LOCK) {
            return;
        }
        identity.setPasswordLockedUntil(LocalDateTime.now().plusMinutes(LOCK_MINUTES));
        this.updateOne(identity);
        // Counter cleared with the lock, so the next window starts fresh when it expires —
        // otherwise one wrong password after unlocking would immediately re-lock.
        this.clearPasswordFailures(identity.getId());
        log.warn("Password login locked for {} minutes after {} consecutive failures (identity {}).",
                LOCK_MINUTES, failures, identity.getId());
    }

    @Override
    public void clearPasswordFailures(Long identityId) {
        if (identityId != null) {
            cacheService.clear(failureKey(identityId));
        }
    }
}
