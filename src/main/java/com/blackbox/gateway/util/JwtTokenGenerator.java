package com.blackbox.gateway.util;

import com.blackbox.gateway.config.GatewayProperties;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

/**
 * JWT token generator for testing.
 * In production, tokens would come from an identity provider (Azure AD, Auth0,
 * etc).
 * This exists solely to make manual and automated testing easy.
 */
@Component
@RequiredArgsConstructor
public class JwtTokenGenerator {

    private final GatewayProperties properties;

    /**
     * Generate a valid JWT for testing.
     *
     * @param clientId The subject (client identifier)
     * @param tier     STANDARD, PREMIUM, or INTERNAL
     * @param name     Human-readable client name
     * @return Signed JWT string
     */
    public String generateToken(String clientId, String tier, String name) {
        SecretKey key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(clientId)
                .claims(Map.of(
                        "tier", tier,
                        "name", name))
                .issuedAt(new Date())
                .expiration(new Date(System.currentTimeMillis() + properties.getJwt().getExpirationMs()))
                .signWith(key)
                .compact();
    }

    /**
     * Generate an expired JWT for testing rejection.
     */
    public String generateExpiredToken(String clientId) {
        SecretKey key = Keys.hmacShaKeyFor(
                properties.getJwt().getSecret().getBytes(StandardCharsets.UTF_8));

        return Jwts.builder()
                .subject(clientId)
                .issuedAt(new Date(System.currentTimeMillis() - 7200000)) // 2 hours ago
                .expiration(new Date(System.currentTimeMillis() - 3600000)) // 1 hour ago
                .signWith(key)
                .compact();
    }
}
