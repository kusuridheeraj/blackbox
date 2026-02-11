package com.blackbox.gateway.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.core.io.ClassPathResource;
import org.springframework.scripting.support.ResourceScriptSource;

/**
 * Redis configuration.
 * Uses StringRedisTemplate for simplicity — all our rate limit operations
 * work with string keys and numeric values via Lua scripts.
 */
@Configuration
public class RedisConfig {

    @Bean
    public StringRedisTemplate stringRedisTemplate(RedisConnectionFactory connectionFactory) {
        return new StringRedisTemplate(connectionFactory);
    }

    /**
     * Lua script for atomic token bucket operations.
     * Loaded once at startup, executed per request.
     */
    @Bean
    public DefaultRedisScript<Long> tokenBucketScript() {
        DefaultRedisScript<Long> script = new DefaultRedisScript<>();
        script.setScriptSource(new ResourceScriptSource(
                new ClassPathResource("scripts/token_bucket.lua")));
        script.setResultType(Long.class);
        return script;
    }
}
