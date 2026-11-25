package com.campusquest.common.web;

import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.MDC;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

/**
 * Who is calling, as far as the server can tell. The client IP is {@code getRemoteAddr()}: forwarded headers
 * are only honoured when {@code server.forward-headers-strategy} is configured for a trusted proxy, so
 * spoofing {@code X-Forwarded-For} cannot dodge rate limits.
 */
public record RequestMetadata(String ip, String userAgent) {

    public static final String REQUEST_ID_MDC_KEY = "requestId";

    public static RequestMetadata of(HttpServletRequest request) {
        return new RequestMetadata(request.getRemoteAddr(), truncate(request.getHeader("User-Agent"), 255));
    }

    /** Metadata of the request bound to the current thread, or an empty record outside a request. */
    public static RequestMetadata current() {
        if (RequestContextHolder.getRequestAttributes() instanceof ServletRequestAttributes attributes) {
            return of(attributes.getRequest());
        }
        return new RequestMetadata(null, null);
    }

    public static String currentRequestId() {
        return MDC.get(REQUEST_ID_MDC_KEY);
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
