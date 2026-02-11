package com.blackbox.gateway.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Central configuration for the BLACKBOX gateway.
 * All tunable parameters live here — no magic numbers in code.
 */
@Data
@Configuration
@ConfigurationProperties(prefix = "gateway")
public class GatewayProperties {

    private Jwt jwt = new Jwt();
    private List<Route> routes = new ArrayList<>();
    private RateLimit rateLimit = new RateLimit();

    @Data
    public static class Jwt {
        private String secret;
        private long expirationMs = 3600000; // 1 hour
    }

    @Data
    public static class Route {
        private String id;
        private String pathPrefix;
        private String targetUrl;
    }

    @Data
    public static class RateLimit {
        private int defaultRequestsPerSecond = 100;
        private int defaultBurstSize = 150;
        private Map<String, TierConfig> tiers = new HashMap<>();
    }

    @Data
    public static class TierConfig {
        private int requestsPerSecond;
        private int burstSize;
    }
}
