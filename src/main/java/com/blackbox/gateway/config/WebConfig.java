package com.blackbox.gateway.config;

import com.blackbox.gateway.filter.JwtAuthFilter;
import com.blackbox.gateway.filter.RateLimitFilter;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Registers gateway filters in the correct order:
 * 1. JWT Authentication (order 1) — reject unauthenticated requests first
 * 2. Rate Limiting (order 2) — check limits only for authenticated clients
 *
 * This ordering is intentional: we want to identify the client (via JWT)
 * before applying per-client rate limits.
 */
@Configuration
public class WebConfig {

    @Bean
    public FilterRegistrationBean<JwtAuthFilter> jwtFilter(JwtAuthFilter filter) {
        FilterRegistrationBean<JwtAuthFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(1);
        return registration;
    }

    @Bean
    public FilterRegistrationBean<RateLimitFilter> rateLimitFilter(RateLimitFilter filter) {
        FilterRegistrationBean<RateLimitFilter> registration = new FilterRegistrationBean<>();
        registration.setFilter(filter);
        registration.addUrlPatterns("/api/*");
        registration.setOrder(2);
        return registration;
    }

    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }
}
