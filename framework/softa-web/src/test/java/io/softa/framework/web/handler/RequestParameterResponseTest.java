package io.softa.framework.web.handler;

import java.util.Map;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;
import org.springframework.core.MethodParameter;
import org.springframework.http.ResponseEntity;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.UnsatisfiedServletRequestParameterException;
import org.springframework.web.method.annotation.MethodArgumentTypeMismatchException;

import io.softa.framework.base.enums.ResponseCode;
import io.softa.framework.web.response.ApiResponse;
import io.softa.framework.web.response.ApiResponseErrorDetails;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A request parameter that is missing or of the wrong type is the caller's mistake: a 400 naming the
 * parameter, not the 500 "System exception" both used to fall through to.
 */
class RequestParameterResponseTest {

    @Test
    void aMissingParameterIsABadRequestThatNamesIt() {
        ApiResponseErrorDetails<Void> body = body(handler().handleException(
                new MissingServletRequestParameterException("id", "Long")));

        assertThat(body.getCode()).isEqualTo(ResponseCode.BAD_REQUEST.getCode());
        assertThat(body.getError()).contains("'id'");
    }

    /** Unmet parameter conditions were a 400 before the handler took their parent class, and stay one. */
    @Test
    void unmetParameterConditionsStayABadRequest() {
        ApiResponseErrorDetails<Void> body = body(handler().handleException(
                new UnsatisfiedServletRequestParameterException(new String[] {"mode=copy"}, Map.of())));

        assertThat(body.getCode()).isEqualTo(ResponseCode.BAD_REQUEST.getCode());
        assertThat(body.getError()).contains("mode=copy");
    }

    @Test
    void aParameterOfTheWrongTypeIsABadRequestThatNamesItAndTheValue() throws NoSuchMethodException {
        MethodParameter parameter = new MethodParameter(
                RequestParameterResponseTest.class.getDeclaredMethod("deleteById", Long.class), 0);
        ApiResponseErrorDetails<Void> body = body(handler().handleException(
                new MethodArgumentTypeMismatchException("abc", Long.class, "id", parameter, null)));

        assertThat(body.getCode()).isEqualTo(ResponseCode.BAD_REQUEST.getCode());
        assertThat(body.getError()).isEqualTo("Parameter 'id' must be a Long, got 'abc'.");
    }

    private static WebExceptionHandler handler() {
        WebExceptionHandler handler = new WebExceptionHandler();
        RequestInfoHandler requestInfo = Mockito.mock(RequestInfoHandler.class);
        Mockito.when(requestInfo.getRequestInfo()).thenReturn("");
        ReflectionTestUtils.setField(handler, "messageHandler", requestInfo);
        return handler;
    }

    @SuppressWarnings("unchecked")
    private static ApiResponseErrorDetails<Void> body(ResponseEntity<ApiResponse<Void>> response) {
        return (ApiResponseErrorDetails<Void>) response.getBody();
    }

    /** Stands in for the endpoint whose parameter failed to bind. */
    @SuppressWarnings("unused")
    private void deleteById(Long id) {
    }
}
