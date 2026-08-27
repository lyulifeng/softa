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
import io.softa.starter.user.service.UserInvitationService;
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

    /** The /join endpoints delegate the token work to the invitation service. */
    @Autowired
    private UserInvitationService invitationService;

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
    public ApiResponse<AuthenticationResult> loginByEmail(@RequestBody @Valid EmailCodeDTO emailCodeDTO,
            HttpServletResponse response) {
        return this.issueOrAskForCompany(
                loginService.authenticateByCode(emailCodeDTO.getEmail(), emailCodeDTO.getCode()), response);
    }

    /**
     * Login by mobile verification code
     * Set cookie with session id
     */
    @PostMapping("/loginByMobileCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<AuthenticationResult> loginByMobileCode(@RequestBody @Valid MobileCodeDTO mobileCodeDTO,
            HttpServletResponse response) {
        return this.issueOrAskForCompany(
                loginService.authenticateByCode(mobileCodeDTO.getMobile(), mobileCodeDTO.getCode()), response);
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
    public ApiResponse<AuthenticationResult> loginByPassword(@RequestBody @Valid EmailPasswordDTO userNameLoginDTO,
            HttpServletResponse response) {
        return this.issueOrAskForCompany(
                loginService.authenticateByPassword(userNameLoginDTO.getEmail(),
                        userNameLoginDTO.getPassword()), response);
    }

    /**
     * The /join landing check — may this token proceed, and if not, why (PRD §3.0).
     *
     * <p>Public by necessity: whoever opened the link has no session yet. It reveals only masked
     * contacts and the company name, so a leaked token yields recognition, not usable details.
     */
    @Operation(summary = "Check an invitation link and return what the join page should show")
    @PostMapping("/joinEntry")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<JoinEntry> joinEntry(@RequestParam @NotNull String token) {
        return ApiResponse.success(invitationService.inspectJoinToken(token));
    }

    /**
     * Send a verification code to the channel the invitation names, without the caller ever seeing
     * the address. The join page only has masked contacts, so it cannot use the plaintext
     * send-code endpoints — and it must not, or a leaked link would become an address oracle.
     */
    @Operation(summary = "Send a verification code to the invitation's own email or mobile")
    @PostMapping("/sendJoinCode")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<Void> sendJoinCode(@RequestParam @NotNull String token,
            @RequestParam @NotNull String channel) {
        loginService.sendJoinCode(token, channel);
        return ApiResponse.success();
    }

    /**
     * Confirm joining, after the person verified their identity (and set a password if new).
     *
     * <p>A session is issued here, not earlier: activation and "you are now in" are the same
     * moment. Verifying a code proves control of the invitation; joining is the agreement.
     */
    @Operation(summary = "Accept the invitation: bind the person, activate the membership, sign in")
    @PostMapping("/confirmJoin")
    @SwitchUser(SystemUser.REGISTERED_USER)
    public ApiResponse<AuthenticationResult> confirmJoin(@RequestParam @NotNull String token,
            @RequestParam @NotNull Long profileId, HttpServletResponse response) {
        invitationService.confirmJoin(token, profileId);
        // Re-runs the company resolution rather than assuming the just-joined membership is the
        // only one: the person may already belong elsewhere, in which case they must still choose.
        return this.issueOrAskForCompany(loginService.afterJoin(profileId), response);
    }

    /**
     * Issue the session when authentication resolved to one membership; otherwise hand back the
     * choice for the client to present.
     *
     * <p>Shared by all three authentication endpoints so the "one or many" decision cannot differ
     * between channels — a path that issued a session without going through here would bypass the
     * whole point of the company step.
     *
     * <p>The response carries {@code profileId} either way: the client needs it for
     * {@code selectCompany}, and it is also what makes {@code mustSetPassword} actionable.
     */
    private ApiResponse<AuthenticationResult> issueOrAskForCompany(AuthenticationResult result,
            HttpServletResponse response) {
        if (result.isResolved()) {
            String sessionId = loginService.generateSessionId(result.userInfo().getUserId());
            CookieUtils.setCookie(response, BaseConstant.SESSION_ID, sessionId);
        }
        return ApiResponse.success(result);
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
    public ApiResponse<AuthenticationResult> selectCompany(@RequestParam @NotNull Long profileId,
            @RequestParam @NotNull Long accountId, HttpServletResponse response) {
        Long resolved = loginService.selectCompany(profileId, accountId);
        // Same shape as the authentication endpoints, so the client has one response contract to
        // handle rather than two — including mustSetPassword, which still applies after choosing.
        return this.issueOrAskForCompany(
                AuthenticationResult.resolved(profileId, profileService.getUserInfo(resolved),
                        loginService.mustSetPassword(profileId)),
                response);
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