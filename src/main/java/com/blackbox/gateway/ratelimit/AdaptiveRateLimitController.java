package com.blackbox.gateway.ratelimit;

import com.blackbox.gateway.metrics.GatewayMetrics;
import com.blackbox.gateway.model.AuditLog;
import com.blackbox.gateway.model.AuditLogRepository;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Adaptive Rate Limit Controller.
 *
 * This is the "self-healing" brain of BLACKBOX.
 * Runs every 10 seconds and adjusts rate limits based on downstream health:
 *
 * Rules:
 * - error_rate > 50% → TIGHTENED mode: halve refill rate (aggressive
 * protection)
 * - error_rate > 20% → CAUTIOUS mode: reduce refill rate by 20%
 * - error_rate < 5% for 2 minutes → RECOVERING mode: gradually restore
 * - otherwise → NORMAL mode: use default limits
 *
 * Design decision: Simple heuristics over ML/AI.
 * Reasons:
 * 1. Explainability: "We tightened because error rate was 62%" > "the model
 * decided"
 * 2. Debuggability: Every adjustment is logged with the exact reason
 * 3. Tunability: Thresholds can be changed without retraining
 *
 * Trade-off accepted: May throttle legitimate users during backend incidents.
 * Protecting the system > perfect fairness. Recovery is automatic.
 */
@Slf4j
@Component
public class AdaptiveRateLimitController {

    private final StringRedisTemplate redisTemplate;
    private final GatewayMetrics metrics;
    private final AuditLogRepository auditLogRepository;

    // Tracking state
    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong errorRequests = new AtomicLong(0);
    private final AtomicReference<AdaptiveMode> currentMode = new AtomicReference<>(AdaptiveMode.NORMAL);
    private final AtomicLong lastNormalTimestamp = new AtomicLong(System.currentTimeMillis());
    private volatile double currentMultiplier = 1.0;

    // Recovery requires sustained low error rate
    private static final long RECOVERY_COOLDOWN_MS = 120_000; // 2 minutes of good health
    private static final double TIGHTEN_THRESHOLD = 0.50; // 50% error rate
    private static final double CAUTION_THRESHOLD = 0.20; // 20% error rate
    private static final double RECOVERY_THRESHOLD = 0.05; // 5% error rate

    public enum AdaptiveMode {
        NORMAL, // Default limits, system healthy
        CAUTIOUS, // Slightly reduced limits, early warning
        TIGHTENED, // Aggressively reduced limits, system under stress
        RECOVERING // Gradually restoring limits after incident
    }

    public AdaptiveRateLimitController(StringRedisTemplate redisTemplate,
            GatewayMetrics metrics,
            AuditLogRepository auditLogRepository) {
        this.redisTemplate = redisTemplate;
        this.metrics = metrics;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Record a request outcome for the adaptive controller.
     * Called by the request router after each forwarded request.
     */
    public void recordRequest(boolean success) {
        totalRequests.incrementAndGet();
        if (!success) {
            errorRequests.incrementAndGet();
        }
    }

    /**
     * Get the current rate limit multiplier.
     * The TokenBucketRateLimiter uses this to adjust its effective limits.
     *
     * 1.0 = normal limits
     * 0.5 = halved limits (TIGHTENED)
     * 0.8 = slightly reduced (CAUTIOUS)
     */
    public double getCurrentMultiplier() {
        return currentMultiplier;
    }

    /**
     * Get the current adaptive mode for observability.
     */
    public AdaptiveMode getCurrentMode() {
        return currentMode.get();
    }

    /**
     * Runs every 10 seconds. Evaluates system health and adjusts limits.
     */
    @Scheduled(fixedRate = 10_000)
    public void evaluateAndAdjust() {
        long total = totalRequests.getAndSet(0);
        long errors = errorRequests.getAndSet(0);

        if (total == 0) {
            // No traffic — nothing to evaluate
            return;
        }

        double errorRate = (double) errors / total;
        AdaptiveMode previousMode = currentMode.get();
        AdaptiveMode newMode;
        double newMultiplier;

        if (errorRate > TIGHTEN_THRESHOLD) {
            // CRITICAL: More than half of requests failing
            newMode = AdaptiveMode.TIGHTENED;
            newMultiplier = 0.5;
            log.warn("ADAPTIVE: TIGHTENED mode — error rate: {} ({}/{} requests)",
                    String.format("%.1f%%", errorRate * 100), errors, total);

        } else if (errorRate > CAUTION_THRESHOLD) {
            // WARNING: Significant error rate
            newMode = AdaptiveMode.CAUTIOUS;
            newMultiplier = 0.8;
            log.info("ADAPTIVE: CAUTIOUS mode — error rate: {} ({}/{} requests)",
                    String.format("%.1f%%", errorRate * 100), errors, total);

        } else if (errorRate < RECOVERY_THRESHOLD) {
            long healthyDuration = System.currentTimeMillis() - lastNormalTimestamp.get();

            if (previousMode != AdaptiveMode.NORMAL && healthyDuration < RECOVERY_COOLDOWN_MS) {
                // Good but not long enough — recovering
                newMode = AdaptiveMode.RECOVERING;
                newMultiplier = Math.min(currentMultiplier * 1.2, 1.0); // Gradually increase
                log.info("ADAPTIVE: RECOVERING — error rate: {}, restoring gradually",
                        String.format("%.1f%%", errorRate * 100));
            } else {
                // Healthy for long enough or was already normal
                newMode = AdaptiveMode.NORMAL;
                newMultiplier = 1.0;
                lastNormalTimestamp.set(System.currentTimeMillis());
            }

        } else {
            // Error rate between 5% and 20% — maintain current mode
            newMode = previousMode;
            newMultiplier = currentMultiplier;
        }

        // Apply changes
        if (newMode != previousMode || Math.abs(newMultiplier - currentMultiplier) > 0.01) {
            currentMode.set(newMode);
            currentMultiplier = newMultiplier;

            // Publish to Redis so all gateway instances see the same multiplier
            publishMultiplier(newMultiplier, newMode);

            // Record metric
            String direction = newMultiplier < currentMultiplier ? "tighten" : "relax";
            metrics.recordAdaptiveAdjustment(direction);

            // Audit log
            logAdjustment(previousMode, newMode, errorRate, total, errors, newMultiplier);

            log.info("ADAPTIVE: {} → {} (multiplier: {}, error rate: {})",
                    previousMode, newMode, newMultiplier, String.format("%.1f%%", errorRate * 100));
        }
    }

    private void publishMultiplier(double multiplier, AdaptiveMode mode) {
        try {
            redisTemplate.opsForValue().set("blackbox:adaptive:multiplier",
                    String.valueOf(multiplier));
            redisTemplate.opsForValue().set("blackbox:adaptive:mode",
                    mode.name());
        } catch (Exception e) {
            log.warn("Failed to publish adaptive multiplier to Redis: {}", e.getMessage());
            // Non-critical — local multiplier still works for this instance
        }
    }

    private void logAdjustment(AdaptiveMode from, AdaptiveMode to,
            double errorRate, long total, long errors, double multiplier) {
        try {
            AuditLog auditLog = AuditLog.builder()
                    .eventType("RATE_LIMIT_ADJUST")
                    .source("AdaptiveRateLimitController")
                    .details(String.format(
                            "{\"from\":\"%s\",\"to\":\"%s\",\"errorRate\":%.4f," +
                                    "\"totalRequests\":%d,\"errorRequests\":%d,\"multiplier\":%.2f}",
                            from, to, errorRate, total, errors, multiplier))
                    .timestamp(Instant.now())
                    .build();
            auditLogRepository.save(auditLog);
        } catch (Exception e) {
            log.warn("Failed to write audit log: {}", e.getMessage());
            // Non-critical — we don't fail the adjustment because audit write failed
        }
    }
}
