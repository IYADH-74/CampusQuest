package com.campusquest.security.ratelimit;

import com.campusquest.common.error.ApiException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.web.servlet.HandlerExceptionResolver;

/** Coarse per-IP ceiling on all API traffic. Runs before authentication so it also shields the token checks. */
public class ApiRateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final HandlerExceptionResolver exceptionResolver;

    public ApiRateLimitFilter(RateLimiter rateLimiter, HandlerExceptionResolver exceptionResolver) {
        this.rateLimiter = rateLimiter;
        this.exceptionResolver = exceptionResolver;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        return !request.getRequestURI().startsWith("/api/");
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            rateLimiter.consume(RateLimitPolicy.API_PER_IP, request.getRemoteAddr());
        } catch (ApiException e) {
            exceptionResolver.resolveException(request, response, null, e);
            return;
        }
        chain.doFilter(request, response);
    }
}
