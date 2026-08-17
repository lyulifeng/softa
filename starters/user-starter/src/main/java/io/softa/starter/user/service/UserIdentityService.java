package io.softa.starter.user.service;

import java.util.Optional;

import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.entity.UserIdentity;

/**
 * CRUD for {@link UserIdentity}. Intentionally thin — the credential rules (hashing, "no stored
 * password ≠ any password fits", loud failure on a missing link) live in {@link UserCredentialService},
 * which is the single seam every password path goes through. This service is only the persistence.
 */
public interface UserIdentityService extends EntityService<UserIdentity, Long> {

    /** The credentials of the person a membership points at, resolved from {@code account.profileId}. */
    Optional<UserIdentity> findByProfileId(Long profileId);

    /** A person by an email OR a dial-code mobile — the seam the identifier-login release switches to. */
    Optional<UserIdentity> findByLoginEmail(String loginEmail);

    Optional<UserIdentity> findByLoginMobile(String loginMobile);
}
