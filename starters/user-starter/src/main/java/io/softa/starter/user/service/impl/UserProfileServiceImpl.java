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
import io.softa.framework.orm.annotation.SkipPermissionCheck;
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
     *
     * <p><b>Why {@code @SkipPermissionCheck}</b>: the filter below pins the row to
     * {@code Context.getUserId()}, so the only thing row scope can still do is take that one row
     * away. {@code UserProfile} is anchorless — a person has no department, no employee, nothing a
     * scope rule can reach it through — so a role with no explicit rule on it fails closed to
     * {@code matchNone()} and the caller cannot read their own profile. What authorizes this call is
     * that the caller is authenticated, which is already asserted: {@code /UserProfile/getMy*} is
     * listed in {@code permission.authenticated-bypass-patterns}, declaring these endpoints open to
     * every logged-in user. That declaration only opened the endpoint gate; this closes the same
     * question at the data layer, where it was still being answered "no".
     *
     * <p>The waiver is safe because it cannot be widened by input: the id comes from the request
     * context, never from a parameter. Contrast {@link #getUserInfo(Long)}, which takes the id as an
     * argument and therefore stays checked — see {@link #getMyUserInfo()}.
     *
     * <p>Note this is also the fetch step of {@link #saveMyProfile}, which carries its own waiver —
     * the flag does not survive this method's return, so the write path cannot borrow this one.
     */
    @SkipPermissionCheck
    @Override
    public UserProfile getCurrentUserProfile() {
        Long userId = ContextHolder.getContext().getUserId();
        Filters profileFilters = new Filters().eq(UserProfile::getUserId, userId);
        Optional<UserProfile> profileOpt = this.searchOne(profileFilters);
        return profileOpt.orElseThrow(() -> new IllegalArgumentException("Current user profile not found."));
    }

    /**
     * Get Current User Profile as Map
     *
     * <p>Waived for the same reason as {@link #getCurrentUserProfile()}, and with the same bound:
     * the filter is built from {@code Context.getUserId()}. Field masking goes with the row check,
     * which is correct here — masking a person's own details from themselves has no reader to
     * protect, and there is nothing secret left on this row anyway (see the note below).
     */
    @SkipPermissionCheck
    @Override
    public Map<String, Object> getCurrentUserProfileMap() {
        Long userId = ContextHolder.getContext().getUserId();
        Filters profileFilters = new Filters().eq(UserProfile::getUserId, userId);
        FlexQuery flexQuery = new FlexQuery(profileFilters);
        flexQuery.setConvertType(ConvertType.REFERENCE);
        Optional<Map<String, Object>> profileOpt = this.modelService.searchOne(this.modelName, flexQuery);
        Map<String, Object> profile = profileOpt
                .orElseThrow(() -> new IllegalArgumentException("Current user profile not found."));
        // No credential fields to strip any more: the password hash, salt and login identifiers moved
        // to UserIdentity. A profile map is now just the person's own display information.
        return profile;
    }

    /**
     * <p><b>Why {@code @SkipPermissionCheck}, and why the write moved here</b>: the waiver aspect
     * restores the flag when the annotated method returns, so a controller that fetched through the
     * waived {@link #getCurrentUserProfile()} and then called a bare {@code updateOne} had only half
     * its work covered — the fetch succeeded and the save failed closed, on the same anchorless
     * model, for the same caller-pinned row. One service method makes the read-modify-write a single
     * waived span.
     *
     * <p>The waiver is bounded on both sides: the row is fetched by {@code Context.getUserId()}
     * (never a parameter), and the field-copy below is the write whitelist — the DTO carries only
     * the person's own display fields, so no tenant, membership or credential value can arrive
     * through this endpoint however the payload is crafted. Credentials live on {@code UserIdentity}
     * and are changed via {@code changeMyPassword}, which verifies the current password first.
     */
    @SkipPermissionCheck
    @Override
    public void saveMyProfile(UserProfileDTO myProfileDTO) {
        UserProfile profile = getCurrentUserProfile();
        profile.setFullName(myProfileDTO.getFullName());
        profile.setChineseName(myProfileDTO.getChineseName());
        profile.setBirthDate(myProfileDTO.getBirthDate());
        profile.setBirthTime(myProfileDTO.getBirthTime());
        profile.setBirthCity(myProfileDTO.getBirthCity());
        profile.setGender(myProfileDTO.getGender());
        profile.setPhotoId(myProfileDTO.getPhotoId());
        profile.setLanguage(myProfileDTO.getLanguage());
        profile.setTimezone(myProfileDTO.getTimezone());
        // updateOne(profile, false) — nulls overwrite. The one-arg overload drops null keys before
        // they reach the update (BeanTool.objectToMap(entity, true)), which is the right default when
        // an entity is only partially populated: a Java object cannot tell "not supplied" from
        // "clear this". Here it is wrong, because the entity above IS fully populated — every column
        // is either the value just read or the value the caller sent — so a null can only mean the
        // caller cleared the field. With the default, clearing silently does nothing: the avatar the
        // helper text says appears "in the workspace header, comments, approvals, and people
        // directories" cannot be removed, and the optional birth details cannot be taken back. Free
        // text escaped it only by accident — the form sends "" for a cleared string, and "" is not
        // null. Safe because the fetch selects every column: the three fields the DTO does not carry
        // (id, userId, density) are written back exactly as read.
        this.updateOne(profile, false);
        // The cached UserInfo carries name / language / timezone / photo — all editable here.
        this.evictUserInfo(profile.getUserId());
    }

    /**
     * Get UserInfo from cache or database
     *
     * @param userId User ID
     * @return UserInfo object
     */
    /**
     * <p><b>Why {@code @SkipPermissionCheck}</b>: same reasoning as {@link #getCurrentUserProfile()}
     * — the id comes from the request context, and the lookup this delegates to reads the caller's
     * own {@code UserProfile}, which is anchorless and therefore fails closed without an explicit
     * rule. This one matters most: {@code getUserInfo} sits on the login path, and its result is
     * cached for a month, so the failure is delayed rather than immediate. A session established
     * while the cache was warm keeps working and only the uncached reads (the personal-settings
     * dialog) fail — until the entry expires, at which point login itself starts refusing the user.
     */
    @SkipPermissionCheck
    @Override
    public UserInfo getMyUserInfo() {
        return this.getUserInfo(ContextHolder.getContext().getUserId());
    }

    /**
     * Deliberately NOT waived: {@code userId} is a parameter. Self-service callers go through
     * {@link #getMyUserInfo()}; the authenticated paths that legitimately pass another id
     * (login, OAuth callback) run before a permission snapshot exists, which
     * {@code PermissionServiceImpl} already treats as a bypass.
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
    /**
     * <p><b>Why {@code @SkipPermissionCheck}</b>: everything written below is bookkeeping this call
     * mints for itself — the person, the membership's link to them, and their credentials row. Row
     * scope answers "which existing rows may this caller reach", and none of these existed a moment
     * ago, so for them the question is not unanswered but unanswerable: no rule can put an id in
     * scope that the same call is about to generate. Left checked, {@code checkIdsFieldsAccess}
     * fails closed on {@code UserProfile} and {@code UserIdentity} — nothing a role holds references
     * either — so a role that may legitimately create an employee ("create employee" is the granted
     * action) is stopped halfway through provisioning that employee's login.
     *
     * <p>What actually authorized this is the action that led here, checked where it happened: the
     * endpoint gate on {@code /Employee/createOne}, on {@code /UserAccount/create}, on the OAuth
     * callback. This method is never an entry point of its own.
     *
     * <p>The waiver stops at this method. The business entity that caused the provisioning — the
     * Employee row — is created by the caller and keeps its own CREATE scope check, so a role scoped
     * to one department subtree still cannot place an employee outside it. Same reasoning, and same
     * annotation, as {@code UserIdentityServiceImpl.requireIdentity}.
     */
    @SkipPermissionCheck
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