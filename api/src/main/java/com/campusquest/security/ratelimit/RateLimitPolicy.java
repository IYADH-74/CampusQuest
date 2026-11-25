package com.campusquest.security.ratelimit;

import java.time.Duration;

/** Fixed-window limits. See {@code docs/security.md}. */
public enum RateLimitPolicy {
    API_PER_IP(600, Duration.ofMinutes(1)),
    LOGIN_PER_IP(20, Duration.ofMinutes(5)),
    LOGIN_FAILURES_PER_EMAIL(5, Duration.ofMinutes(15)),
    REGISTER_PER_IP(5, Duration.ofHours(1)),
    VERIFY_PER_IP(10, Duration.ofMinutes(15)),
    VERIFY_PER_EMAIL(10, Duration.ofMinutes(15)),
    FORGOT_PASSWORD_PER_IP(5, Duration.ofHours(1)),
    FORGOT_PASSWORD_PER_EMAIL(3, Duration.ofHours(1)),
    RESET_PASSWORD_PER_IP(10, Duration.ofMinutes(15)),
    REFRESH_PER_IP(60, Duration.ofMinutes(5)),
    CHANGE_PASSWORD_PER_USER(5, Duration.ofMinutes(15)),
    ACCOUNT_NOTICE_PER_EMAIL(1, Duration.ofHours(1));

    private final int limit;
    private final Duration window;

    RateLimitPolicy(int limit, Duration window) {
        this.limit = limit;
        this.window = window;
    }

    public int limit() {
        return limit;
    }

    public Duration window() {
        return window;
    }
}
