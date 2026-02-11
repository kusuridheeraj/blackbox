package com.blackbox.gateway.ratelimit;

import com.blackbox.gateway.config.GatewayProperties;
import com.blackbox.gateway.model.ClientIdentity;
import com.blackbox.gateway.model.RateLimitDecision;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;

import java.util.Collections;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Redis-backed Token Bucket Rate Limiter.
 *
 * Uses a Lua script for atomic check-and-decrement operations in Redis.
 * This ensures correctness even with multiple gateway instances hitting
 * the same Redis — no race conditions.
 *
 * If Redis is unavailable, falls back to a local in-memory counter with
 * CONSERVATIVE limits (50% of normal). This is the "fail-safe" behavior
 * described in the architecture doc.
 *
 * Day 2: Basic implementation with static limits
 * Day 3: Integrated with AdaptiveRateLimitController — limits adjust
 * dynamically based on downstream health.
 */
@Slf4j
@Component
public class TokenBucketRateLimiter implements RateLimiter {

    private final StringRedisTemplate redisTemplate;
    private final DefaultRedisScript<Long> tokenBucketScript;
    private final GatewayProperties properties;
    private final AdaptiveRateLimitController adaptiveController;

    // Local fallback state (used when Redis is down)
    private final ConcurrentHashMap<String, AtomicInteger> localCounters = new ConcurrentHashMap<>();
    private volatile long lastCounterReset = System.currentTimeMillis();
    private static final long LOCAL_WINDOW_MS = 1000; // 1 second window

    public TokenBucketRateLimiter(StringRedisTemplate redisTemplate,
            DefaultRedisScript<Long> tokenBucketScript,
            GatewayProperties properties,
            AdaptiveRateLimitController adaptiveController) {
        this.redisTemplate = redisTemplate;
        this.tokenBucketScript = tokenBucketScript;
        this.properties = properties;
        this.adaptiveController = adaptiveController;
    }

    @Override
    public RateLimitDecision tryAcquire(ClientIdentity identity, String routeId) {
        try {
            return tryAcquireFromRedis(identity, routeId);
        } catch (Exception e) {
            log.warn("Redis rate limiter unavailable, using local fallback: {}", e.getMessage());
            return tryAcquireLocal(identity, routeId);
        }
    }

    /**
     * Primary path: Redis-backed token bucket via Lua script.
     */
    private RateLimitDecision tryAcquireFromRedis(ClientIdentity identity, String routeId) {
        String key = buildRedisKey(identity.getClientId(), routeId);
        int baseMaxTokens = getMaxTokensForTier(identity.getTier());
        int baseRefillRate = getRefillRateForTier(identity.getTier());

        // Apply adaptive multiplier — shrinks limits when system is stressed
        double multiplier = adaptiveController.getCurrentMultiplier();
        int effectiveMaxTokens = (int) Math.max(1, baseMaxTokens * multiplier);
        int effectiveRefillRate = (int) Math.max(1, baseRefillRate * multiplier);

        // Execute atomic Lua script: check tokens, decrement if available
        Long result = redisTemplate.execute(
                tokenBucketScript,
                Collections.singletonList(key),
                String.valueOf(effectiveMaxTokens),
                String.valueOf(effectiveRefillRate),
                String.valueOf(System.currentTimeMillis()));

        if (result == null || result < 0) {
            long retryAfter = Math.max(1, 1000 / effectiveRefillRate);
            return RateLimitDecision.denied(effectiveMaxTokens, retryAfter, "redis");
        }

        return RateLimitDecision.allowed(result, effectiveMaxTokens, "redis");
    }

    /**
     * Fallback path: Simple in-memory counter when Redis is down.
     * Uses 50% of normal limits — conservative to protect the backend
     * while still allowing some traffic through.
     */
    private RateLimitDecision tryAcquireLocal(ClientIdentity identity, String routeId) {
        // Reset counters every second
        long now = System.currentTimeMillis();
        if (now - lastCounterReset > LOCAL_WINDOW_MS) {
            localCounters.clear();
            lastCounterReset = now;
        }

        String key = identity.getClientId() + ":" + routeId;
        int maxTokens = getMaxTokensForTier(identity.getTier()) / 2; // 50% capacity in fallback

        AtomicInteger counter = localCounters.computeIfAbsent(key, k -> new AtomicInteger(0));
        int current = counter.incrementAndGet();

        if (current > maxTokens) {
            return RateLimitDecision.denied(maxTokens, 1, "local-fallback");
        }

        return RateLimitDecision.allowed(maxTokens - current, maxTokens, "local-fallback");
    }

    private String buildRedisKey(String clientId, String routeId) {
        return "ratelimit:" + clientId + ":" + routeId;
    }

    private int getMaxTokensForTier(String tier) {
        GatewayProperties.TierConfig tierConfig = properties.getRateLimit().getTiers().get(tier);
        if (tierConfig != null) {
            return tierConfig.getBurstSize();
        }
        return properties.getRateLimit().getDefaultBurstSize();
    }

    private int getRefillRateForTier(String tier) {
        GatewayProperties.TierConfig tierConfig = properties.getRateLimit().getTiers().get(tier);
        if (tierConfig != null) {
            return tierConfig.getRequestsPerSecond();
        }
        return properties.getRateLimit().getDefaultRequestsPerSecond();
    }
}
