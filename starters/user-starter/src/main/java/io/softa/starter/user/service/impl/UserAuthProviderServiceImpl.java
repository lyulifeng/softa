package io.softa.starter.user.service.impl;

import java.util.Optional;
import org.springframework.stereotype.Service;

import io.softa.framework.base.utils.JsonUtils;
import io.softa.framework.orm.annotation.CrossTenant;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.entity.UserAuthProvider;
import io.softa.starter.user.enums.OAuthProvider;
import io.softa.starter.user.service.UserAuthProviderService;

/**
 * UserAuthProvider Model Service Implementation
 */
@Service
public class UserAuthProviderServiceImpl extends EntityServiceImpl<UserAuthProvider, Long> implements UserAuthProviderService {

    /**
     * Get user id by provider and providerId
     *
     * <p>Cross-tenant of necessity: this is the "have I seen this social account before?" question,
     * asked by the OAuth callback BEFORE anyone is authenticated. There is no tenant in context to
     * match on, so with {@code UserAuthProvider} isolated by tenant the added {@code tenant_id = null}
     * predicate would match nothing — every returning social user would look new, be registered
     * again, and collect a duplicate account on each login.
     *
     * <p>Narrow by construction rather than by trust: the filter is an exact match on
     * (provider, providerUserId), a pair the identity provider issued, and the method returns a user
     * id and nothing else. The caller then reads that user through the ordinary tenant-aware path.
     *
     * @param provider Provider
     * @param providerId Provider ID
     * @return UserAuthProvider
     */
    @CrossTenant
    @Override
    public Optional<Long> getUserIdByAuthProvider(OAuthProvider provider, String providerId) {
        Filters filters = new Filters()
                .eq(UserAuthProvider::getProvider, provider.getProvider())
                .eq(UserAuthProvider::getProviderUserId, providerId);
        FlexQuery flexQuery = new FlexQuery(filters).select(UserAuthProvider::getUserId);
        return this.searchOne(flexQuery).map(UserAuthProvider::getUserId);
    }

    /**
     * Add auth provider
     *
     * @param userId User ID
     * @param provider OAuth Provider
     * @param providerId Provider ID
     * @param additionalInfo Additional Info
     */
    @Override
    public void addAuthProvider(Long userId, OAuthProvider provider, String providerId, Object additionalInfo) {
        UserAuthProvider userAuthProvider = new UserAuthProvider();
        userAuthProvider.setUserId(userId);
        userAuthProvider.setProvider(provider);
        userAuthProvider.setProviderUserId(providerId);
        userAuthProvider.setAdditionalInfo(JsonUtils.objectToString(additionalInfo));
        this.createOne(userAuthProvider);
    }
}