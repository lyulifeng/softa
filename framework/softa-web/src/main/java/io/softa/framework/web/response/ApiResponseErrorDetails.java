package io.softa.framework.web.response;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Data;
import lombok.EqualsAndHashCode;

import io.softa.framework.base.context.ContextHolder;
import io.softa.framework.base.enums.ResponseCode;

/**
 * API response body with error details:
 *    {
 *      "code": 440,
 *      "message": "...category label of the response code...",
 *      "data": [{...}],
 *      "error": "...user-facing error message...",
 *      "traceId": "...server-side trace ID..."
 *    }
 * The code represents the business status code of the response.
 * The message is the generic label of the response code (the error category).
 * The data is an optional error payload for programmatic handling.
 * The error carries the user-facing reason; the traceId lets support correlate
 * the response with the server logs (every exception log line includes it).
 */
@Data
@Schema(name = "API Response Body")
@EqualsAndHashCode(callSuper = true)
public class ApiResponseErrorDetails<T> extends ApiResponse<T> {

    @Schema(description = "Error Details")
    private String error;

    @Schema(description = "Server-side trace ID for log correlation")
    private String traceId;

    private ApiResponseErrorDetails(Integer code, String message, T data, String error) {
        super(code, message, data);
        this.error = error;
        // The context always carries a trace ID: taken from the client's
        // X-B3-TraceId header when present, generated otherwise.
        this.traceId = ContextHolder.getContext().getTraceId();
    }

    /**
     * Generate exception response body, with the provided responseCode object and error message.
     *
     * @param responseCode response code object.
     * @param error error message of the response.
     * @return ApiResponse<T>
     */
    public static ApiResponseErrorDetails<Void> exception(ResponseCode responseCode, String error) {
        Integer code = responseCode.getCode();
        String message = responseCode.getMessage();
        return new ApiResponseErrorDetails<>(code, message, null, error);
    }

    /**
     * Generate exception response body, with the provided responseCode object and error message.
     *
     * @param responseCode response code object.
     * @param data data of the response.
     * @param error error message of the response.
     * @return ApiResponse<T>
     * @param <T> data type.
     */
    public static <T> ApiResponseErrorDetails<T> exception(ResponseCode responseCode, T data, String error) {
        Integer code = responseCode.getCode();
        String message = responseCode.getMessage();
        return new ApiResponseErrorDetails<>(code, message, data, error);
    }
}
