package com.blackbox.gateway.filter;

import com.blackbox.gateway.metrics.GatewayMetrics;
import com.blackbox.gateway.model.ClientIdentity;
import com.blackbox.gateway.model.GatewayErrorResponse;
import com.blackbox.gateway.model.RateLimitDecision;
import com.blackbox.gateway.ratelimit.RateLimiter;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;

/**
 * Rate Limit Filter.
 *
 * Runs AFTER JwtAuthFilter (order 2), so the client identity is already
 * available on the request. Uses the identity's tier for per-client limits.
 *
 * Sets standard rate limit headers on every response:
 * - X-RateLimit-Limit: maximum requests per window
 * - X-RateLimit-Remaining: remaining requests in current window
 * - X-RateLimit-Source: which limiter made the decision (redis vs
 * local-fallback)
 * - Retry-After: seconds to wait (only on 429)
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RateLimitFilter extends OncePerRequestFilter {

    private final RateLimiter rateLimiter;
    private final GatewayMetrics gatewayMetrics;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        ClientIdentity identity = (ClientIdentity) request.getAttribute(ClientIdentity.REQUEST_ATTRIBUTE);

        // If no identity (shouldn't happen — JWT filter runs first), allow through
        if (identity == null) {
            log.error("Rate limit filter invoked without client identity — JWT filter may have been bypassed");
            filterChain.doFilter(request, response);
            return;
        }

        String routeId = extractRouteId(request.getRequestURI());
        RateLimitDecision decision = rateLimiter.tryAcquire(identity, routeId);

        // Always set rate limit headers (even on denied requests)
        response.setHeader("X-RateLimit-Limit", String.valueOf(decision.getLimit()));
        response.setHeader("X-RateLimit-Remaining", String.valueOf(decision.getRemaining()));
        response.setHeader("X-RateLimit-Source", decision.getSource());

        if (!decision.isAllowed()) {
            log.info("Rate limited client: {} on route: {} (source: {})",
                    identity.getClientId(), routeId, decision.getSource());

            response.setHeader("Retry-After", String.valueOf(decision.getRetryAfterSeconds()));
            gatewayMetrics.recordThrottle(routeId, identity.getTier());

            GatewayErrorResponse error = GatewayErrorResponse.builder()
                    .status(HttpStatus.TOO_MANY_REQUESTS.value())
                    .error("Too Many Requests")
                    .message("Rate limit exceeded. Retry after " + decision.getRetryAfterSeconds() + " seconds.")
                    .path(request.getRequestURI())
                    .retryAfterSeconds(decision.getRetryAfterSeconds())
                    .build();

            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            objectMapper.writeValue(response.getWriter(), error);
            return;
        }

        filterChain.doFilter(request, response);
    }

    /**
     * Extract route ID from URI path.
     * /api/users/123 → "api" (first path segment after /)
     */
    private String extractRouteId(String uri) {
        String[] parts = uri.split("/");
        if (parts.length >= 2) {
            return parts[1]; // "api"
        }
        return "default";
    }
}
