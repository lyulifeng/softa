package io.softa.starter.user.service;

import java.util.Map;

import io.softa.framework.base.context.UserInfo;
import io.softa.framework.orm.dto.FileInfo;
import io.softa.framework.orm.service.EntityService;
import io.softa.starter.user.dto.UserProfileDTO;
import io.softa.starter.user.entity.UserProfile;

/**
 * UserProfile Model Service Interface
 */
public interface UserProfileService extends EntityService<UserProfile, Long> {

    /**
     * Get Current User Profile
     */
    UserProfile getCurrentUserProfile();

    /**
     * Get Current User Profile as Map
     */
    Map<String, Object> getCurrentUserProfileMap();

    /**
     * Update the caller's own profile from the self-service DTO.
     *
     * <p>Lives on the service so the read-modify-write runs inside ONE row-scope waiver: the
     * controller used to fetch via {@code getCurrentUserProfile()} and then call a bare
     * {@code updateOne}, and since the waiver aspect restores the flag when the fetch returns, the
     * write half still failed closed on this anchorless model — the dialog opened and the save
     * bounced. The DTO is the write boundary: only the person's own display fields exist on it, so
     * nothing tenant- or credential-shaped can arrive however the payload is crafted.
     */
    void saveMyProfile(UserProfileDTO myProfileDTO);

    /**
     * Get UserInfo from cache or database
     *
     * @param userId User ID
     * @return UserInfo object
     */
    UserInfo getUserInfo(Long userId);

    /**
     * The caller's own {@link UserInfo}.
     *
     * <p>Exists so the self-service read can waive row scope without widening
     * {@link #getUserInfo(Long)}, which takes the id as an argument. Every current caller of that
     * method happens to pass the caller's own id, but nothing in its signature says so — waiving it
     * would make "any person's UserInfo" readable the moment someone writes an admin-facing lookup
     * and reuses it, and the result is cached for a month, so the leak would outlive the request.
     */
    UserInfo getMyUserInfo();

    /**
     * Drop a user's cached {@code UserInfo} so the next read rebuilds it from the database.
     *
     * <p>Must be called whenever something the snapshot carries changes — above all
     * {@code UserAccount.status}, which {@code buildUserInfo} folds into {@code UserInfo.active}.
     * {@code ContextBuilder} re-checks that flag on <b>every</b> request and terminates the session
     * when it is false, while the login gate reads the account row from the database: leave the
     * cache stale and the two disagree for the full one-month TTL. An account activated after an
     * invitation then passes login but is kicked out by the very next request, and — the dangerous
     * direction — a frozen account keeps working because the per-request gate never sees the new
     * status.
     *
     * @param userId User ID; ignored when null
     */
    void evictUserInfo(Long userId);

    /**
     * Register new user profile when user register
     *
     * @param userId User ID
     * @param profileDTO User profile DTO
     * @return UserInfo object
     */
    UserInfo registerUserProfile(Long userId, UserProfileDTO profileDTO);

    /**
     * Create the PERSON for a first-time invitee, identified only by the address their invitation
     * was sent to.
     *
     * <p>Separate from {@link #registerUserProfile} because that path starts from an account and
     * copies its work contacts onto the person. On /join the order is reversed: the person is
     * created before they agree to join, so no membership may be touched yet — binding happens in
     * {@code confirmJoin}, and doing it here would make "I verified a code" mean "I accepted".
     *
     * <p>Nothing but the identifier is filled in. Their name and details belong to the employee
     * record their company maintains; guessing them here would create a second source of truth.
     *
     * @return the new person's id
     */
    Long createPersonForJoin(String identifier);

    /**
     * Fetch user photo from remote URL and save it locally
     *
     * @param photoUrl Photo URL
     * @param profileId UserProfile ID
     * @return FileInfo of the saved photo
     */
    FileInfo fetchPhotoFromURL(String photoUrl, Long profileId);

}