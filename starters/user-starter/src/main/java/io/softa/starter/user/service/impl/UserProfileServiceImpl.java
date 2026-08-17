package io.softa.starter.user.service.impl;

import java.util.Map;
import java.util.Optional;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;

import io.softa.framework.base.constant.RedisConstant;
import io.softa.framework.base.constant.StringConstant;
import io.softa.framework.base.context.Context;
import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.exception.BusinessException;
import io.softa.framework.base.exception.IllegalArgumentException;
import io.softa.framework.base.utils.Assert;
import io.softa.framework.base.utils.LambdaUtils;
import io.softa.framework.orm.domain.Filters;
import io.softa.framework.orm.domain.FlexQuery;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.orm.enums.ConvertType;
import io.softa.framework.orm.meta.ModelManager;
import io.softa.framework.orm.service.CacheService;
import io.softa.framework.orm.service.FileService;
import io.softa.framework.orm.service.TenantInfoService;
import io.softa.framework.orm.service.impl.EntityServiceImpl;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserAccount;
import io.softa.starter.user.entity.UserIdentity;
import io.softa.starter.user.entity.UserProfile;
import io.softa.starter.user.enums.AccountStatus;
import io.softa.starter.user.service.UserAccountService;
import io.softa.starter.user.service.UserIdentityService;
import io.softa.starter.user.service.UserProfileService;

/**
 * UserProfile Model Service Implementation
 */
@Slf4j
@Service
public class UserProfileServiceImpl extends EntityServiceImpl<UserProfile, Long> implements UserProfileService {

    @Autowired
    private FileService fileService;

    @Autowired
    private CacheService cacheService;

    @Autowired
    private TenantInfoService tenantInfoService;

    /** @Lazy breaks a potential UserAccount ⇄ UserProfile service cycle; used only to
     *  read the account's status when (re)building the cached UserInfo. */
    @Autowired
    @Lazy
    private UserAccountService accountService;

    /** Credentials live on UserIdentity, a 1:1 satellite created alongside the profile at
     *  registration; this creates and seeds that row. */
    @Autowired
    private UserIdentityService identityService;

    /**
     * Get Current User Profile
     */
    @Override
    public UserProfile getCurrentUserProfile() {
        Long userId = ContextHolder.getContext().getUserId();
        Filters profileFilters = new Filters().eq(UserProfile::getUserId, userId);
        Optional<UserProfile> profileOpt = this.searchOne(profileFilters);
        return profileOpt.orElseThrow(() -> new IllegalArgumentException("Current user profile not found."));
    }

    /**
     * Get Current User Profile as Map
     */
    @Override
    public Map<String, Object> getCurrentUserProfileMap() {
        Long userId = ContextHolder.getContext().getUserId();
        Filters profileFilters = new Filters().eq(UserProfile::getUserId, userId);
        FlexQuery flexQuery = new FlexQuery(profileFilters);
        flexQuery.setConvertType(ConvertType.REFERENCE);
        Optional<Map<String, Object>> profileOpt = this.modelService.searchOne(this.modelName, flexQuery);
        Map<String, Object> profile = profileOpt
                .orElseThrow(() -> new IllegalArgumentException("Current user profile not found."));
        // No credential fields to strip any more: the password hash, salt and login identifiers
        // moved to UserIdentity, which has no API surface at all. A profile map is now just the
        // person's own display information, safe to hand back to their browser.
        return profile;
    }

    /**
     * Get UserInfo from cache or database
     *
     * @param userId User ID
     * @return UserInfo object
     */
    @Override
    public UserInfo getUserInfo(Long userId) {
        // Check and potentially update UserInfo cache
        String userInfoKey = RedisConstant.USER_INFO + userId;
        UserInfo userInfo = cacheService.get(userInfoKey, UserInfo.class);
        if (userInfo == null) {
            Filters filters = new Filters().eq(UserProfile::getUserId, userId);
            UserProfile profile = this.searchOne(filters).orElseThrow(
                    () -> new BusinessException("User profile not found for user ID: " + userId));
            userInfo = this.buildUserInfo(profile);
            this.refreshUserInfo(userId, userInfo);
        }
        return userInfo;
    }

    @Override
    public void evictUserInfo(Long userId) {
        if (userId == null) {
            return;
        }
        cacheService.clear(RedisConstant.USER_INFO + userId);
    }

    /**
     * Refresh UserInfo cache
     *
     * @param userId User ID
     * @param userInfo UserInfo object
     */
    private void refreshUserInfo(Long userId, UserInfo userInfo) {
        String userInfoKey = RedisConstant.USER_INFO + userId;
        cacheService.save(userInfoKey, userInfo, RedisConstant.ONE_MONTH);
    }

    /**
    * Build UserInfo from UserProfile and save to cache
    *
    * @param profile UserProfile
    * @return UserInfo object
    */
    private UserInfo buildUserInfo(UserProfile profile) {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(profile.getUserId());
        userInfo.setName(profile.getFullName());
        userInfo.setLanguage(profile.getLanguage());
        userInfo.setTimezone(profile.getTimezone());
        // The tenant now comes from the MEMBERSHIP, not the person: a person is global, and which
        // company this session is in is exactly what the account represents. One lookup serves both
        // this and the live-status check below.
        Optional<UserAccount> account = accountService.getById(profile.getUserId());
        userInfo.setTenantId(account.map(UserAccount::getTenantId).orElse(null));
        // Reflect the account's live status so ContextBuilder can force-logout a frozen account.
        // No account row → NOT active: ContextBuilder reads this to decide force-logout, and a
        // session whose account has vanished should be logged out, not waved through. (The old
        // orElse(TRUE) here was also dead under multi-tenancy — validateTenantInfo below asserts a
        // non-null tenantId first and would already have thrown.)
        userInfo.setActive(account.map(a -> AccountStatus.ACTIVE == a.getStatus()).orElse(Boolean.FALSE));
        this.validateTenantInfo(profile, userInfo.getTenantId());
        if (profile.getPhotoId() != null) {
            // The photo URL expires in one quarter (90 days), longer than the user info cache expiration time
            Optional<FileInfo> fileInfoOpt = fileService.getByFileId(profile.getPhotoId(), RedisConstant.ONE_QUARTER);
            fileInfoOpt.ifPresent(fileInfo -> userInfo.setPhotoUrl(fileInfo.getUrl()));
        }
        return userInfo;
    }

    private void validateTenantInfo(UserProfile profile, Long tenantId) {
        if (ModelManager.isMultiTenantControl()) {
            Assert.notNull(tenantId,
                    "UserProfile(id = {0}) has no membership to take a tenant from; "
                            + "a person must belong to at least one company to sign in.",
                    profile.getId());
            Assert.notNull(tenantInfoService,
                    "Multi-tenant control is enabled but no TenantInfoService is available; "
                            + "ensure tenant-starter is on the classpath.");
            Assert.isTrue(tenantInfoService.isTenantActive(tenantId),
                    "Tenant with tenantId {0} is not active", tenantId);
        }
    }

    /**
     * Build UserProfile from UserProfileDTO
     *
     * @param profileInfo UserProfileDTO
     * @return UserProfile object
     */
    private UserProfile buildUserProfile(UserProfileDTO profileInfo) {
        Context context = ContextHolder.getContext();
        UserProfile userProfile = new UserProfile();
        userProfile.setFullName(profileInfo.getFullName());
        userProfile.setChineseName(profileInfo.getChineseName());
        userProfile.setGender(profileInfo.getGender());
        userProfile.setBirthDate(profileInfo.getBirthDate());
        userProfile.setBirthTime(profileInfo.getBirthTime());
        userProfile.setBirthCity(profileInfo.getBirthCity());
        userProfile.setPhotoId(profileInfo.getPhotoId());
        userProfile.setLanguage(Optional.ofNullable(profileInfo.getLanguage()).orElse(context.getLanguage()));
        userProfile.setTimezone(Optional.ofNullable(profileInfo.getTimezone()).orElse(context.getTimezone()));
        // No tenant stamped here any more: a person is global, and the tenant is held by the
        // UserAccount that represents their membership.
        return userProfile;
    }

    /**
     * Register new user profile when user register
     *
     * @param userId User ID
     * @param profileDTO User profile DTO
     * @return UserInfo object
     */
    @Override
    public UserInfo registerUserProfile(Long userId, UserProfileDTO profileDTO) {
        // Create user profile
        UserProfile userProfile = this.buildUserProfile(profileDTO);
        userProfile.setUserId(userId);

        // Loudly, not orElse(null): silently skipping here would return success for a person
        // who can never authenticate — no identity row, and no profileId link below. The one
        // caller that legitimately has no account does not exist; every path creates the
        // account first.
        UserAccount account = accountService.getById(userId).orElseThrow(
                () -> new BusinessException(
                        "Account " + userId + " not found — a profile cannot be registered before its account."));
        Long profileId = this.createOne(userProfile);
        userProfile.setId(profileId);

        // Point the membership at the person. UserAccount.profileId is the relation now
        // (UserProfile.userId is the legacy back-reference kept for the migration); skipping this
        // would leave an account whose credentials cannot be resolved at all — every password path
        // fails with "not linked to a person".
        account.setProfileId(profileId);
        accountService.updateOne(account);

        // Create the person's UserIdentity (1:1 satellite) and seed the LOGIN identifiers from the
        // account's work contacts, in the same transaction — a person is not fully created until
        // their credentials row exists, and requireIdentity resolves through it.
        //
        // The identifiers are not read by anything yet — login still resolves an account by its
        // email, exactly as before. They are populated from day one so that the release which DOES
        // resolve people by identifier needs no backfill: the expensive part of that change is the
        // data, and this is the one moment where every new person passes through a single place.
        UserIdentity identity = new UserIdentity();
        identity.setProfileId(profileId);
        identity.setLoginEmail(StringUtils.trimToNull(account.getEmail()));
        identity.setLoginMobile(StringUtils.trimToNull(account.getMobile()));
        identityService.createOne(identity);

        // Build UserInfo and upload photo if photo is not empty
        UserInfo userInfo = this.buildUserInfo(userProfile);

        // Update UserInfo cache
        this.refreshUserInfo(userId, userInfo);

        return userInfo;
    }

    /**
     * Fetch user photo from remote URL and save it locally
     *
     * @param photoUrl Photo URL
     * @param profileId Profile ID
     * @return FileInfo of the saved photo
     */
    @Override
    public FileInfo fetchPhotoFromURL(String photoUrl, Long profileId) {
        if (StringUtils.isNotBlank(photoUrl) && (
                photoUrl.startsWith(StringConstant.HTTP_PREFIX)
                        || photoUrl.startsWith(StringConstant.HTTPS_PREFIX))) {
            String fieldName = LambdaUtils.getAttributeName(UserProfile::getPhotoId);
            try {
                // The photo URL expires in one quarter (90 days), longer than the user info cache expiration time
                return fileService.uploadFromUrl(this.modelName, profileId, fieldName, photoUrl, RedisConstant.ONE_QUARTER);
            } catch (Exception e) {
                log.error("Failed to upload photo from URL: {}", photoUrl, e);
            }
        }
        return null;
    }

}