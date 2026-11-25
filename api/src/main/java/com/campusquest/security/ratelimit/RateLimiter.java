package com.campusquest.security.ratelimit;

import com.campusquest.common.error.ApiException;
import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.time.Duration;
import java.util.EnumMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.stereotype.Component;

/**
 * In-memory fixed-window rate limiter (one bounded Caffeine cache per policy). A window starts at the first
 * hit for a key and expires {@code window} later. Single-instance by design. Swap this bean for a Redis-backed
 * one to scale out.
 */
@Component
public class RateLimiter {

    private static final int MAX_KEYS_PER_POLICY = 200_000;

    private final Map<RateLimitPolicy, Cache<String, AtomicInteger>> windows = new EnumMap<>(RateLimitPolicy.class);

    public RateLimiter() {
        for (RateLimitPolicy policy : RateLimitPolicy.values()) {
            windows.put(policy, Caffeine.newBuilder()
                    .expireAfterWrite(policy.window())
                    .maximumSize(MAX_KEYS_PER_POLICY)
                    .build());
        }
    }

    /** Counts a hit and throws 429 once the limit is exceeded. */
    public void consume(RateLimitPolicy policy, String key) {
        if (key == null) {
            return;
        }
        int count = windows.get(policy).get(key, k -> new AtomicInteger()).incrementAndGet();
        if (count > policy.limit()) {
            throw ApiException.tooManyRequests(retryAfter(policy, key));
        }
    }

    /** Counts a hit and reports whether it was within the limit, without throwing. */
    public boolean tryConsume(RateLimitPolicy policy, String key) {
        if (key == null) {
            return true;
        }
        return windows.get(policy).get(key, k -> new AtomicInteger()).incrementAndGet() <= policy.limit();
    }

    /** Throws 429 if the budget is already spent, without counting a hit (used for failure counters). */
    public void requireBudget(RateLimitPolicy policy, String key) {
        AtomicInteger count = windows.get(policy).getIfPresent(key);
        if (count != null && count.get() >= policy.limit()) {
            throw ApiException.tooManyRequests(retryAfter(policy, key));
        }
    }

    public void reset(RateLimitPolicy policy, String key) {
        windows.get(policy).invalidate(key);
    }

    private Duration retryAfter(RateLimitPolicy policy, String key) {
        return windows.get(policy).policy().expireAfterWrite()
                .flatMap(expiry -> expiry.ageOf(key).map(age -> policy.window().minus(age)))
                .filter(d -> !d.isNegative())
                .orElse(policy.window());
    }
}
