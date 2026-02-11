package com.blackbox.gateway.ratelimit;

import com.blackbox.gateway.model.ClientIdentity;
import com.blackbox.gateway.model.RateLimitDecision;

/**
 * Rate limiter contract.
 * Multiple implementations exist:
 * - TokenBucketRateLimiter: Redis-backed, production path
 * - LocalFallbackRateLimiter: In-memory, used when Redis is down
 */
public interface RateLimiter {

    /**
     * Check if a request from the given client should be allowed.
     *
     * @param identity The authenticated client
     * @param routeId  The target route (for per-route limits)
     * @return Decision with allow/deny + metadata
     */
    RateLimitDecision tryAcquire(ClientIdentity identity, String routeId);
}
