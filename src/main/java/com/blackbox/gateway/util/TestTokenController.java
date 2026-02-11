package com.blackbox.gateway.util;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

/**
 * Test helper endpoint to generate JWT tokens.
 * Only available in dev/test profiles — NOT for production.
 *
 * Usage:
 * curl -X POST http://localhost:8080/test/token \
 * -H "Content-Type: application/json" \
 * -d '{"clientId":"client-1","tier":"STANDARD","name":"Test Client"}'
 */
@RestController
@RequestMapping("/test")
@RequiredArgsConstructor
public class TestTokenController {

    private final JwtTokenGenerator tokenGenerator;

    @PostMapping("/token")
    public ResponseEntity<Map<String, String>> generateToken(@RequestBody Map<String, String> request) {
        String clientId = request.getOrDefault("clientId", "test-client");
        String tier = request.getOrDefault("tier", "STANDARD");
        String name = request.getOrDefault("name", "Test Client");

        String token = tokenGenerator.generateToken(clientId, tier, name);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "clientId", clientId,
                "tier", tier,
                "usage", "Authorization: Bearer " + token));
    }

    @PostMapping("/token/expired")
    public ResponseEntity<Map<String, String>> generateExpiredToken(@RequestBody Map<String, String> request) {
        String clientId = request.getOrDefault("clientId", "test-client");
        String token = tokenGenerator.generateExpiredToken(clientId);

        return ResponseEntity.ok(Map.of(
                "token", token,
                "note", "This token is already expired — use for testing 401 rejection"));
    }
}
