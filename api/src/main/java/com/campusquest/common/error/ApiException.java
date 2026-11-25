package com.campusquest.common.error;

import java.time.Duration;
import org.springframework.http.HttpStatus;

/**
 * An expected, client-facing failure. Carries a stable machine-readable {@code code} that ends up in the
 * RFC 9457 problem body. No stack trace is captured: these are control flow, not bugs.
 */
public class ApiException extends RuntimeException {

    private final HttpStatus status;
    private final String code;
    private final Duration retryAfter;

    public ApiException(HttpStatus status, String code, String detail) {
        this(status, code, detail, null);
    }

    private ApiException(HttpStatus status, String code, String detail, Duration retryAfter) {
        super(detail, null, false, false);
        this.status = status;
        this.code = code;
        this.retryAfter = retryAfter;
    }

    public static ApiException notFound(String resource) {
        return new ApiException(HttpStatus.NOT_FOUND, "NOT_FOUND", resource + " not found");
    }

    public static ApiException badRequest(String code, String detail) {
        return new ApiException(HttpStatus.BAD_REQUEST, code, detail);
    }

    public static ApiException unauthorized(String code, String detail) {
        return new ApiException(HttpStatus.UNAUTHORIZED, code, detail);
    }

    public static ApiException forbidden(String code, String detail) {
        return new ApiException(HttpStatus.FORBIDDEN, code, detail);
    }

    public static ApiException conflict(String code, String detail) {
        return new ApiException(HttpStatus.CONFLICT, code, detail);
    }

    public static ApiException tooManyRequests(Duration retryAfter) {
        return new ApiException(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED",
                "Too many requests. Try again later.", retryAfter);
    }

    public HttpStatus status() {
        return status;
    }

    public String code() {
        return code;
    }

    public Duration retryAfter() {
        return retryAfter;
    }
}
