package com.blackbox.gateway.filter;

import com.blackbox.gateway.config.GatewayProperties;
import com.blackbox.gateway.model.ClientIdentity;
import com.blackbox.gateway.model.GatewayErrorResponse;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
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

import javax.crypto.SecretKey;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * JWT Authentication Filter.
 *
 * Validates the Authorization: Bearer <token> header on every /api/* request.
 * Extracts client identity (clientId, tier, name) from JWT claims and attaches
 * it to the request for downstream use by the rate limiter and router.
 *
 * Design decision: We validate JWT at the gateway edge so that NO
 * unauthenticated
 * request ever reaches the backend. This is a security boundary.
 *
 * Failure behavior: Invalid/expired/missing token → 401 immediately.
 * No fallback, no retry. Authentication is non-negotiable.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final GatewayProperties gatewayProperties;
    private final ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain) throws ServletException, IOException {

        String authHeader = request.getHeader("Authorization");

        // No token → reject
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            sendError(response, request.getRequestURI(),
                    HttpStatus.UNAUTHORIZED, "Missing or invalid Authorization header");
            return;
        }

        String token = authHeader.substring(7);

        try {
            SecretKey key = Keys.hmacShaKeyFor(
                    gatewayProperties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));

            Claims claims = Jwts.parser()
                    .verifyWith(key)
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            // Extract client identity from JWT claims
            ClientIdentity identity = ClientIdentity.builder()
                    .clientId(claims.getSubject())
                    .tier(claims.get("tier", String.class) != null
                            ? claims.get("tier", String.class)
                            : "STANDARD")
                    .name(claims.get("name", String.class) != null
                            ? claims.get("name", String.class)
                            : claims.getSubject())
                    .build();

            // Attach identity to request for downstream filters
            request.setAttribute(ClientIdentity.REQUEST_ATTRIBUTE, identity);

            log.debug("Authenticated client: {} (tier: {})", identity.getClientId(), identity.getTier());

            filterChain.doFilter(request, response);

        } catch (ExpiredJwtException e) {
            log.warn("Expired JWT from client: {}", e.getClaims().getSubject());
            sendError(response, request.getRequestURI(),
                    HttpStatus.UNAUTHORIZED, "Token expired");
        } catch (JwtException e) {
            log.warn("Invalid JWT: {}", e.getMessage());
            sendError(response, request.getRequestURI(),
                    HttpStatus.UNAUTHORIZED, "Invalid token");
        }
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
