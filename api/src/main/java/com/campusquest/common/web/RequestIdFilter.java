package com.campusquest.common.web;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.UUID;
import java.util.regex.Pattern;
import org.slf4j.MDC;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Correlates logs, errors and audit rows. An incoming {@code X-Request-Id} is only reused when it is short
 * and plain, so it cannot be used for log injection.
 */
public class RequestIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Request-Id";
    private static final Pattern SAFE_ID = Pattern.compile("^[A-Za-z0-9._-]{8,64}$");

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        String incoming = request.getHeader(HEADER);
        String requestId = incoming != null && SAFE_ID.matcher(incoming).matches()
                ? incoming
                : UUID.randomUUID().toString();
        MDC.put(RequestMetadata.REQUEST_ID_MDC_KEY, requestId);
        response.setHeader(HEADER, requestId);
        try {
            chain.doFilter(request, response);
        } finally {
            MDC.remove(RequestMetadata.REQUEST_ID_MDC_KEY);
        }
    }
}
