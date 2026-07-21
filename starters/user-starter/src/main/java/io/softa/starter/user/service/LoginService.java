package io.softa.starter.user.service;

import io.softa.framework.base.context.UserInfo;
import io.softa.starter.user.dto.InvitationInfo;

/**
 * UserAccount Model Service Interface
 */
public interface LoginService {

    /**
     * Send email verification code
     *
     * @param email Email address
     */
    void sendEmailCode(String email);

    /**
     * Send mobile verification code
     *
     * @param mobile Mobile number
     */
    void sendMobileCode(String mobile);

    /**
     * User login by email verification code
     *
     * @param email Email address
     * @param code  Verification code
     * @return UserInfo
     */
    UserInfo loginByEmailCode(String email, String code);

    /**
     * User login by mobile verification code
     *
     * @param mobile Mobile number
     * @param code   Verification code
     * @return UserInfo
     */
    UserInfo loginByMobileCode(String mobile, String code);

    /**
     * Generate a new session ID for a user
     *
     * @param userId User ID
     * @return Session ID
     */
    String generateSessionId(Long userId);

    /**
     * User registration by email and password
     *
     * @param email    email
     * @param password Password
     * @return UserInfo
     */
    UserInfo registerByEmailAndPassword(String email, String password);

    /**
     * User login by email and password
     *
     * @param email    Email address
     * @param password Password
     * @return UserInfo
     */
    UserInfo loginByEmailAndPassword(String email, String password);

    /**
     * Forgot password — issue a self-service password-reset token and email the set-password link.
     *
     * @param email registered email
     */
    void forgetPassword(String email);

    /**
     * Set the password via a token (invitation-accept or forgot-password reset).
     *
     * @param token       the emailed one-time token
     * @param newPassword the new password
     */
    void resetPassword(String token, String newPassword);

    /**
     * Validate a token for the public set-password page.
     *
     * @param token the emailed one-time token
     * @return validity + the email to greet the holder (no leak of why an invalid token failed)
     */
    InvitationInfo inviteInfo(String token);
}