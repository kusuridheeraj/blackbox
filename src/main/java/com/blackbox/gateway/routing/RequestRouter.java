package com.blackbox.gateway.routing;

import com.blackbox.gateway.circuitbreaker.CircuitBreaker;
import com.blackbox.gateway.circuitbreaker.CircuitState;
import com.blackbox.gateway.config.GatewayProperties;
import com.blackbox.gateway.metrics.GatewayMetrics;
import com.blackbox.gateway.model.GatewayErrorResponse;
import com.blackbox.gateway.ratelimit.AdaptiveRateLimitController;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import java.io.IOException;
import java.time.Duration;
import java.time.Instant;
import java.util.Enumeration;

/**
 * Request Router.
 *
 * Forwards authenticated, rate-limited requests to the appropriate backend.
 * Integrates with the circuit breaker to stop forwarding to unhealthy backends.
 *
 * Design decisions:
 * - Uses WebClient (non-blocking) even though we're on servlet stack,
 * because the standard thread pool handles the blocking wait efficiently.
 * - Hard 5-second timeout per request. Better to fail fast than queue up.
 * - Records all request metrics (latency, status, errors) for observability.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class RequestRouter {

    private final GatewayProperties properties;
    private final WebClient.Builder webClientBuilder;
    private final CircuitBreaker circuitBreaker;
    private final GatewayMetrics metrics;
    private final ObjectMapper objectMapper;
    private final AdaptiveRateLimitController adaptiveController;

    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(5);

    /**
     * Forward the request to the matching backend route.
     */
    public void route(HttpServletRequest request, HttpServletResponse response) throws IOException {
        String uri = request.getRequestURI();
        Instant start = Instant.now();

        // Find matching route
        GatewayProperties.Route route = findRoute(uri);
        if (route == null) {
            sendError(response, uri, HttpStatus.NOT_FOUND, "No route found for: " + uri);
            return;
        }

        // Check circuit breaker
        if (!circuitBreaker.allowRequest(route.getId())) {
            CircuitState state = circuitBreaker.getState(route.getId());
            log.warn("Circuit breaker {} for route: {}", state, route.getId());
            sendError(response, uri, HttpStatus.SERVICE_UNAVAILABLE,
                    "Service temporarily unavailable. Circuit breaker is " + state);
            metrics.recordRequest(route.getId(), 503, Duration.between(start, Instant.now()));
            return;
        }

        // Build target URL
        String targetUrl = route.getTargetUrl() + uri;
        log.debug("Routing {} {} → {}", request.getMethod(), uri, targetUrl);

        try {
            // Forward request
            WebClient client = webClientBuilder.build();
            WebClient.RequestHeadersSpec<?> spec = buildRequest(client, request, targetUrl);

            byte[] responseBody = spec.retrieve()
                    .bodyToMono(byte[].class)
                    .timeout(REQUEST_TIMEOUT)
                    .block();

            // Success — record and forward response
            response.setStatus(HttpStatus.OK.value());
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            if (responseBody != null) {
                response.getOutputStream().write(responseBody);
            }

            circuitBreaker.recordSuccess(route.getId());
            adaptiveController.recordRequest(true); // Feed adaptive controller
            metrics.recordRequest(route.getId(), 200, Duration.between(start, Instant.now()));

        } catch (WebClientResponseException e) {
            int statusCode = e.getStatusCode().value();
            log.warn("Downstream error {} for route: {} — {}", statusCode, route.getId(), e.getMessage());

            response.setStatus(statusCode);
            response.setContentType(MediaType.APPLICATION_JSON_VALUE);
            response.getOutputStream().write(e.getResponseBodyAsByteArray());

            // 5xx errors feed the circuit breaker and adaptive controller
            if (statusCode >= 500) {
                circuitBreaker.recordFailure(route.getId());
                metrics.recordDownstreamError(route.getId());
                adaptiveController.recordRequest(false);
            } else {
                adaptiveController.recordRequest(true);
            }

            metrics.recordRequest(route.getId(), statusCode, Duration.between(start, Instant.now()));

        } catch (Exception e) {
            log.error("Failed to route request to {}: {}", targetUrl, e.getMessage());
            circuitBreaker.recordFailure(route.getId());
            metrics.recordDownstreamError(route.getId());
            adaptiveController.recordRequest(false); // Connection failures are errors
            metrics.recordRequest(route.getId(), 502, Duration.between(start, Instant.now()));

            sendError(response, uri, HttpStatus.BAD_GATEWAY,
                    "Failed to reach downstream service: " + e.getMessage());
        }
    }

    private GatewayProperties.Route findRoute(String uri) {
        return properties.getRoutes().stream()
                .filter(r -> uri.startsWith(r.getPathPrefix()))
                .findFirst()
                .orElse(null);
    }

    private WebClient.RequestHeadersSpec<?> buildRequest(WebClient client,
            HttpServletRequest request,
            String targetUrl) {
        WebClient.RequestBodyUriSpec uriSpec = client.method(
                org.springframework.http.HttpMethod.valueOf(request.getMethod()));

        WebClient.RequestBodySpec bodySpec = uriSpec.uri(targetUrl);

        // Forward relevant headers (skip host and connection)
        Enumeration<String> headerNames = request.getHeaderNames();
        while (headerNames.hasMoreElements()) {
            String name = headerNames.nextElement();
            if (!name.equalsIgnoreCase("host") &&
                    !name.equalsIgnoreCase("connection") &&
                    !name.equalsIgnoreCase("content-length")) {
                bodySpec.header(name, request.getHeader(name));
            }
        }

        return bodySpec;
    }

    private void sendError(HttpServletResponse response, String path,
            HttpStatus status, String message) throws IOException {
        GatewayErrorResponse error = GatewayErrorResponse.builder()
                .status(status.value())
                .error(status.getReasonPhrase())
                .message(message)
                .path(path)
                .build();

        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getWriter(), error);
    }
}
