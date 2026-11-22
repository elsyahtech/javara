package com.elsyah.tech.javara.middleware.store;

import java.time.Duration;
import java.util.List;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

@Component
public class RedisLoginRateLimitStore {
    private static final RedisScript<Long> INCREMENT_AND_CHECK_SCRIPT = new DefaultRedisScript<>("""
            local count = redis.call('INCR', KEYS[1])
            local ttl = redis.call('PTTL', KEYS[1])
            if count == 1 or ttl < 0 then
                ttl = tonumber(ARGV[1])
                redis.call('PEXPIRE', KEYS[1], ttl)
            end
            if count > tonumber(ARGV[2]) then
                return ttl
            end
            return 0
            """, Long.class);

    private final StringRedisTemplate redisTemplate;

    public RedisLoginRateLimitStore(StringRedisTemplate redisTemplate) {
        this.redisTemplate = redisTemplate;
    }

    public long incrementAndGetRetryAfterMillis(
            String key,
            int maxRequests,
            Duration window) {
        if (key == null || key.isBlank()) {
            throw new IllegalArgumentException("Rate limit key must not be blank");
        }
        if (maxRequests < 1) {
            throw new IllegalArgumentException("Maximum requests must be positive");
        }
        if (window == null || window.isZero() || window.isNegative()
                || window.toMillis() < 1) {
            throw new IllegalArgumentException("Rate limit window must be positive");
        }

        Long retryAfterMillis = redisTemplate.execute(
                INCREMENT_AND_CHECK_SCRIPT,
                List.of(key),
                Long.toString(window.toMillis()),
                Integer.toString(maxRequests));
        if (retryAfterMillis == null) {
            throw new IllegalStateException("Redis did not return a rate limit result");
        }
        return retryAfterMillis;
    }
}
