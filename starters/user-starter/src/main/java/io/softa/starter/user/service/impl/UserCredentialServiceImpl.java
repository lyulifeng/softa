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
import io.softa.framework.orm.domain.Filters;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.service.UserCredentialService;
import io.softa.starter.user.service.UserProfileService;

/**
 * {@link UserCredentialService} — reads and writes credentials on the PERSON.
 *
 * <p>{@code @CrossTenant} on the lookups: a login identifier is global by definition, and the
 * caller has no tenant context yet — that is decided AFTER authenticating. Scoping these to a
 * tenant would make "who is this?" answerable only once you already knew.
 *
 * <p>Depends on {@link UserProfileService} only. Not on {@code UserAccountService}, which depends
 * on this one for every password it writes — see {@code requireProfile}.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class UserCredentialServiceImpl implements UserCredentialService {

    private final UserProfileService profileService;

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public UserProfile requireProfile(UserAccount account) {
        if (account == null) {
            throw new BusinessException("Account not found.");
        }
        Long profileId = account.getProfileId();
        if (profileId == null) {
            // After the migration this is a data fault, not a normal state. Treating it as "no
            // password" would let a password-less path through, so refuse loudly instead.
            throw new BusinessException("This account is not linked to a person yet — contact support.");
        }
        return profileService.getById(profileId)
                .orElseThrow(() -> new BusinessException("Person record not found for this account."));
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public Optional<UserProfile> findByLoginIdentifier(String identifier) {
        if (StringUtils.isBlank(identifier)) {
            return Optional.empty();
        }
        Optional<UserProfile> byEmail = profileService.searchOne(
                new Filters().eq(UserProfile::getLoginEmail, identifier));
        if (byEmail.isPresent()) {
            return byEmail;
        }
        return profileService.searchOne(new Filters().eq(UserProfile::getLoginMobile, identifier));
    }

    @Override
    public boolean matchesPassword(UserProfile profile, String rawPassword) {
        if (profile == null || StringUtils.isBlank(profile.getPassword())
                || StringUtils.isBlank(rawPassword)) {
            // No stored password = password login is not available for this person (they arrived by
            // invitation and have not set one). Returning true for an empty hash is the classic way
            // that rule gets broken.
            return false;
        }
        String hashed = PasswordUtils.hashPassword(rawPassword, profile.getPasswordSalt());
        return hashed.equals(profile.getPassword());
    }

    @SkipPermissionCheck
    @CrossTenant
    @Override
    public void setPassword(Long profileId, String rawPassword) {
        UserProfile profile = profileService.getById(profileId)
                .orElseThrow(() -> new BusinessException("Person record not found."));
        String salt = PasswordUtils.generateSalt();
        profile.setPasswordSalt(salt);
        profile.setPassword(PasswordUtils.hashPassword(rawPassword, salt));
        profileService.updateOne(profile);
    }
}
