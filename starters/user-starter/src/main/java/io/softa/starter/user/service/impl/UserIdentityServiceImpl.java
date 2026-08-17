package io.softa.starter.user.service.impl;

import java.util.Optional;

import org.springframework.stereotype.Service;

import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.service.UserIdentityService;

/**
 * Persistence for {@link UserIdentity}. See {@link UserIdentityService} for why this is only CRUD.
 */
@Service
public class UserIdentityServiceImpl extends EntityServiceImpl<UserIdentity, Long>
        implements UserIdentityService {

    @Override
    public Optional<UserIdentity> findByProfileId(Long profileId) {
        if (profileId == null) {
            return Optional.empty();
        }
        return this.searchOne(new Filters().eq(UserIdentity::getProfileId, profileId));
    }

    @Override
    public Optional<UserIdentity> findByLoginEmail(String loginEmail) {
        return this.searchOne(new Filters().eq(UserIdentity::getLoginEmail, loginEmail));
    }

    @Override
    public Optional<UserIdentity> findByLoginMobile(String loginMobile) {
        return this.searchOne(new Filters().eq(UserIdentity::getLoginMobile, loginMobile));
    }
}
