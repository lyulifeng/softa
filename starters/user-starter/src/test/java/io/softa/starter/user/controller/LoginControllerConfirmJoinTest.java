package io.softa.starter.user.controller;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import io.softa.framework.base.context.UserInfo;
import io.softa.framework.web.response.ApiResponse;
import io.softa.starter.user.dto.AuthenticationResult;
import io.softa.starter.user.dto.ConfirmJoinDTO;
import io.softa.starter.user.service.LoginService;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The controller is where the session cookie is minted, so "joined but not signed in" has to hold
 * there too: a {@code signInRequired} result must leave the response untouched.
 */
class LoginControllerConfirmJoinTest {

    private final LoginService loginService = mock(LoginService.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);
    private final LoginController controller = new LoginController();

    LoginControllerConfirmJoinTest() {
        ReflectionTestUtils.setField(controller, "loginService", loginService);
    }

    private static ConfirmJoinDTO request() {
        ConfirmJoinDTO dto = new ConfirmJoinDTO();
        dto.setToken("raw-token");
        dto.setProfileId(7L);
        dto.setProof("proof");
        return dto;
    }

    @Test
    void signInRequired_setsNoSessionCookie() {
        when(loginService.confirmJoin("raw-token", 7L, "proof")).thenReturn(AuthenticationResult.requireSignIn());

        ApiResponse<AuthenticationResult> reply = controller.confirmJoin(request(), response);

        assertThat(reply.getData().signInRequired()).isTrue();
        // Load-bearing: no session id is generated and nothing is written to the response.
        verify(loginService, never()).generateSessionId(any());
        verifyNoInteractions(response);
    }

    @Test
    void aResolvedJoin_stillIssuesTheSession() {
        UserInfo userInfo = new UserInfo();
        userInfo.setUserId(100L);
        when(loginService.confirmJoin("raw-token", 7L, "proof"))
                .thenReturn(AuthenticationResult.resolved(7L, userInfo, false));
        when(loginService.generateSessionId(100L)).thenReturn("session");

        ApiResponse<AuthenticationResult> reply = controller.confirmJoin(request(), response);

        assertThat(reply.getData().signInRequired()).isFalse();
        verify(loginService).generateSessionId(100L);
    }
}
