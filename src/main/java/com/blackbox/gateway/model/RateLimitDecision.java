package com.blackbox.gateway.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Result of a rate limit check.
 * Carries enough context for the filter to make allow/deny decisions
 * and set appropriate response headers.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RateLimitDecision {

    /** Whether the request is allowed */
    private boolean allowed;

    /** Remaining tokens in the bucket */
    private long remaining;

    /** Maximum bucket capacity */
    private long limit;

    /** Seconds until the bucket refills */
    private long retryAfterSeconds;

    /** Which limiter made this decision (redis, local-fallback) */
    private String source;

    public static RateLimitDecision allowed(long remaining, long limit, String source) {
        return RateLimitDecision.builder()
                .allowed(true)
                .remaining(remaining)
                .limit(limit)
                .retryAfterSeconds(0)
                .source(source)
                .build();
    }

    public static RateLimitDecision denied(long limit, long retryAfterSeconds, String source) {
        return RateLimitDecision.builder()
                .allowed(false)
                .remaining(0)
                .limit(limit)
                .retryAfterSeconds(retryAfterSeconds)
                .source(source)
                .build();
    }
}
