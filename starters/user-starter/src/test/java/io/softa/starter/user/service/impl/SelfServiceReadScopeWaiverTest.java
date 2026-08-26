package io.softa.starter.user.service.impl;

import java.lang.reflect.Method;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import io.softa.framework.orm.annotation.SkipPermissionCheck;
import io.softa.starter.user.dto.UserProfileDTO;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Reading your own profile must not be subject to row scope.
 *
 * <p>{@code UserProfile} is anchorless — a person has no department, no employee, nothing a scope
 * rule can reach them through — so a role with no explicit rule on it fails closed to
 * {@code matchNone()}. Every read below already pins the row to {@code Context.getUserId()}, so the
 * only effect the check can have is to take that one row away, and the caller cannot read their own
 * profile. Observed symptom: a user holding a plain business role signs in, and the personal-settings
 * dialog answers {@code 400 "Current user profile not found."} while the row sits in the table with
 * the right {@code user_id}.
 *
 * <p>That the endpoints are open to any authenticated caller is not a new decision here — it is
 * already declared in the application's {@code permission.authenticated-bypass-patterns}
 * ({@code /UserProfile/getMy*}). That configuration only opens the endpoint gate; the annotations
 * these tests pin answer the same question at the data layer, which was still answering "no".
 *
 * <p><b>Why assert this at all</b>: removing an annotation compiles, passes every other test, and
 * fails only for non-admin roles in a live tenant — admins short-circuit the whole check and never
 * reproduce it. {@code getUserInfo} makes that worse by caching for a month, so the break surfaces
 * long after the change, as a login failure rather than a profile failure.
 */
class SelfServiceReadScopeWaiverTest {

    private static Method method(String name, Class<?>... params) throws Exception {
        return UserProfileServiceImpl.class.getDeclaredMethod(name, params);
    }

    @Test
    @DisplayName("context-pinned self-service reads waive row scope")
    void selfServiceReadsWaiveRowScope() throws Exception {
        for (String name : new String[] {"getCurrentUserProfile", "getCurrentUserProfileMap", "getMyUserInfo"}) {
            assertNotNull(method(name).getAnnotation(SkipPermissionCheck.class),
                    "UserProfileServiceImpl." + name + " reads the caller's own row, pinned by "
                            + "Context.getUserId(), and must carry @SkipPermissionCheck — UserProfile is "
                            + "anchorless, so row scope fails closed and the caller loses their own "
                            + "profile. See the method's javadoc.");
        }
    }

    /**
     * The write is a separate assertion because it failed separately: the waiver aspect restores the
     * flag when the annotated method returns, so a controller that fetched through the waived read
     * and then called a bare updateOne had only the fetch covered — the dialog opened and the save
     * bounced. The whole read-modify-write must sit inside one annotated service method.
     */
    @Test
    @DisplayName("the self-service write runs inside one waived span")
    void selfServiceWriteWaivesRowScope() throws Exception {
        assertNotNull(method("saveMyProfile", UserProfileDTO.class).getAnnotation(SkipPermissionCheck.class),
                "UserProfileServiceImpl.saveMyProfile owns the caller's read-modify-write and must carry "
                        + "@SkipPermissionCheck — waiving only the fetch leaves updateOne to fail closed "
                        + "on the same anchorless model, for the same caller-pinned row.");
    }

    @Test
    @DisplayName("the id-taking overload stays checked")
    void parameterisedLookupStaysChecked() throws Exception {
        assertNull(method("getUserInfo", Long.class).getAnnotation(SkipPermissionCheck.class),
                "getUserInfo(Long) takes the id as an argument, so waiving it would make any person's "
                        + "UserInfo readable through whatever caller passes an id next — and the result is "
                        + "cached, so the leak would outlive the request. Self-service callers use "
                        + "getMyUserInfo(); login and OAuth run before a permission snapshot exists, which "
                        + "PermissionServiceImpl already bypasses.");
    }
}
