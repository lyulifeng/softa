package io.softa.starter.user.controller;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.softa.framework.base.constant.BaseConstant;
import io.softa.framework.base.context.UserInfo;
import io.softa.framework.base.enums.ResponseCode;
import io.softa.framework.base.enums.SystemUser;
import io.softa.framework.orm.annotation.SwitchUser;
import io.softa.framework.web.response.ApiResponse;
import io.softa.framework.web.utils.CookieUtils;
import io.softa.starter.user.dto.*;
import io.softa.starter.user.service.LoginService;
import io.softa.starter.user.service.OAuth2Service;

/**
 * Login Controller
 * login, register, forget password, reset password, force-reset password
 */
@Slf4j
@Tag(name = "Login")
@RestController
@RequestMapping("/login")
public class LoginController {

    @Autowired
    private LoginService loginService;

    @Autowired
    private OAuth2Service oAuth2Service;

    /**
     * Login by Apple ID
     * Set cookie with session id
     */
    @PostMapping("/loginByApple")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> loginByApple(@RequestBody @Valid AppleLoginDTO appleLoginDTO,
                    HttpServletResponse response) {
        UserInfo userInfo = oAuth2Service.loginByApple(appleLoginDTO.getToken());
        String sessionId = loginService.generateSessionId(userInfo.getUserId());
        CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        return ApiResponse.success(userInfo);
    }

    /**
     * Login by OAuth2
     * Set cookie with session id
     */
    @PostMapping("/loginByOAuth2")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> loginByOAuth2(@RequestBody @Valid OAuthCredential oAuthCredential,
            HttpServletResponse response) {
        UserInfo userInfo = oAuth2Service.loginByOAuth2(oAuthCredential);
        String sessionId = loginService.generateSessionId(userInfo.getUserId());
        CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        return ApiResponse.success(userInfo);
    }

    /**
     * Login by email verification code
     * Set cookie with session id
     */
    @PostMapping("/loginByEmailCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> loginByEmail(@RequestBody @Valid EmailCodeDTO emailCodeDTO,
            HttpServletResponse response) {
        UserInfo userInfo = loginService.loginByEmailCode(emailCodeDTO.getEmail(), emailCodeDTO.getCode());
        String sessionId = loginService.generateSessionId(userInfo.getUserId());
        CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        return ApiResponse.success(userInfo);
    }

    /**
     * Login by mobile verification code
     * Set cookie with session id
     */
    @PostMapping("/loginByMobileCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> loginByMobileCode(@RequestBody @Valid MobileCodeDTO mobileCodeDTO,
            HttpServletResponse response) {
        UserInfo userInfo = loginService.loginByMobileCode(mobileCodeDTO.getMobile(), mobileCodeDTO.getCode());
        String sessionId = loginService.generateSessionId(userInfo.getUserId());
        CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        return ApiResponse.success(userInfo);
    }

    @PostMapping("/sendEmailCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<Void> sendEmailCode(@RequestBody @Valid SendEmailCodeDTO body) {
        loginService.sendEmailCode(body.getEmail());
        return ApiResponse.success();
    }

    @PostMapping("/sendMobileCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<Void> sendMobileCode(@RequestBody @Valid SendMobileCodeDTO body) {
        loginService.sendMobileCode(body.getMobile());
        return ApiResponse.success();
    }

    /**
     * Register by email and password.
     * Automatically login after successful registration.
     * Set cookie with session id
     */
    @PostMapping("/registerByPassword")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> registerByPassword(@RequestBody @Valid EmailPasswordDTO emailPasswordDTO, HttpServletResponse response) {
        try {
            UserInfo userInfo = loginService.registerByEmailAndPassword(emailPasswordDTO.getEmail(),
                    emailPasswordDTO.getPassword());
            String sessionId = loginService.generateSessionId(userInfo.getUserId());
            CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
            return ApiResponse.success(userInfo);
        } catch (Exception e) {
            log.error("Register error: ", e);
            return ApiResponse.error(ResponseCode.EMAIL_OR_PASSWORD_ERROR, e.getMessage());
        }
    }

    /**
     * Login by email and password
     * Set cookie with session id
     */
    @PostMapping("/loginByPassword")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> loginByPassword(@RequestBody @Valid EmailPasswordDTO userNameLoginDTO,
            HttpServletResponse response) {
        UserInfo userInfo = loginService.loginByEmailAndPassword(userNameLoginDTO.getEmail(),
                        userNameLoginDTO.getPassword());
        String sessionId = loginService.generateSessionId(userInfo.getUserId());
        CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        return ApiResponse.success(userInfo);
    }

    /**
     * Forgot password, send reset password email
     */
    @PostMapping("/forgetPassword")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<Void> forgotPassword(@RequestBody @Valid ForgotPasswordDTO forgotPasswordDTO) {
        loginService.forgetPassword(forgotPasswordDTO.getEmail());
        return ApiResponse.success();
    }

    /**
     * Reset password using the token sent via email
     */
    @PostMapping("/resetPassword")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<Void> resetPassword(@RequestBody @Valid ResetPasswordDTO resetPasswordDTO) {
        loginService.resetPassword(resetPasswordDTO.getToken(), resetPasswordDTO.getNewPassword());
        return ApiResponse.success();
    }

    /**
     * Validate an invitation / reset token for the public set-password page (greets the holder with
     * the email; never reveals why an invalid token failed).
     */
    @PostMapping("/inviteInfo")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<InvitationInfo> inviteInfo(@RequestBody @Valid InviteInfoDTO body) {
        return ApiResponse.success(loginService.inviteInfo(body.getToken()));
    }

}