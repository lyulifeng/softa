package io.softa.framework.orm.service;

import io.softa.framework.base.context.UserInfo;

/**
 * Framework SPI for rebuilding the session-facing {@link UserInfo} of an account. The
 * implementation ({@code UserProfileServiceImpl}) is provided by user-starter; framework consumers
 * (above all {@code ContextBuilder}) depend only on this contract, never on the user entities.
 *
 * <p>It exists so that {@code user-info:{userId}} is a <b>cache</b> rather than the session payload.
 * Every eviction in the codebase is written as a refresh — an invitee flipping INVITED → ACTIVE, a
 * bulk unfreeze, a person renaming themselves, an HR-side employee change — and each one documents
 * the stale value it means to replace. Without a way to rebuild, {@code ContextBuilder} answered a
 * missing entry with "user not found", so every one of those refreshes force-logged-out the very
 * user it was trying to update.
 */
public interface UserInfoService {

    /**
     * Load the account's {@code UserInfo}, from cache when warm and from the database otherwise,
     * re-caching what it builds.
     *
     * @param userId the account id (a membership, not the person)
     * @return the info, or {@code null} when no account / profile backs that id — the session names
     *         a user that no longer exists, which the caller must treat as unauthenticated
     */
    UserInfo loadUserInfo(Long userId);
}
