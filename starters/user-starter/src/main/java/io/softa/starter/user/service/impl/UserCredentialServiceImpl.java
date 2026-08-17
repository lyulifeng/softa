package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Service;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.security.PasswordUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserCredentialService;
import io.softa.starter.user.service.UserIdentityService;

/**
 * {@link UserCredentialService} — reads and writes credentials on the person's {@link UserIdentity}.
 *
 * <p>{@code @CrossTenant} on the lookups: a login identifier is global by definition, and the
 * caller has no tenant context yet — that is decided AFTER authenticating. Scoping these to a
 * tenant would make "who is this?" answerable only once you already knew.
 *
 * <p>Depends on {@link UserIdentityService} only. Not on {@code UserAccountService}, which depends
 * on this one for every password it writes — see {@code requireIdentity}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

    private final UserIdentityService identityService;

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
        return identityService.findByProfileId(profileId)
                .orElseThrow(() -> new BusinessException("Credentials not found for this account."));
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public Optional<UserIdentity> findByLoginIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return Optional.empty();
        }
        Optional<UserIdentity> byEmail = identityService.findByLoginEmail(identifier);
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return identityService.findByLoginMobile(identifier);
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
    public void setPassword(Long identityId, String rawPassword) {
        UserIdentity identity = identityService.getById(identityId)
                .orElseThrow(() -> new BusinessException("Credentials not found."));
        String salt = PasswordUtils.generateSalt();
        identity.setPasswordSalt(salt);
        identity.setPassword(PasswordUtils.hashPassword(rawPassword, salt));
        identityService.updateOne(identity);
    }
}
