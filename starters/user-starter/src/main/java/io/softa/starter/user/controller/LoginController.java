package io.softa.starter.user.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import java.util.List;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
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
import io.softa.starter.user.service.UserProfileService;
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

    /** Builds the session payload once a membership is chosen. */
    @Autowired
    private UserProfileService profileService;

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
     * The companies an authenticated person may enter.
     *
     * <p>Reached when authentication succeeded but the person belongs to more than one company
     * (or to one they cannot enter), so no session was issued yet. Not tenant-scoped by nature —
     * the caller has proven who they are but not yet chosen where they are going.
     */
    @Operation(summary = "List the companies this person can log into (multi-company login step)")
    @PostMapping("/listCompanies")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<List<MembershipOption>> listCompanies(@RequestParam @NotNull Long profileId) {
        return ApiResponse.success(loginService.listCompanies(profileId));
    }

    /**
     * Enter one company, issuing the session.
     *
     * <p>The service verifies the membership really belongs to this person before returning its
     * account id — naming someone else's would otherwise mint a session in a company the caller
     * is not a member of.
     */
    @Operation(summary = "Enter the chosen company and issue the session")
    @PostMapping("/selectCompany")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<UserInfo> selectCompany(@RequestParam @NotNull Long profileId,
            @RequestParam @NotNull Long accountId, HttpServletResponse response) {
        Long resolved = loginService.selectCompany(profileId, accountId);
        UserInfo userInfo = profileService.getUserInfo(resolved);
        String sessionId = loginService.generateSessionId(resolved);
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