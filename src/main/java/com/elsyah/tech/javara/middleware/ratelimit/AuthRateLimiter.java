package com.elsyah.tech.javara.middleware.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.middleware.store.RedisRateLimitStore;

@Component
public class AuthRateLimiter {
    private final RedisRateLimitStore store;

    public AuthRateLimiter(RedisRateLimitStore store) {
        this.store = store;
    }

    public void checkAllowed(String clientIp, RateLimitSettings settings) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Client IP must not be blank");
        }
        if (settings == null) {
            throw new IllegalArgumentException("Rate limit settings must not be null");
        }

        long retryAfterMillis;
        try {
            String key = settings.keyPrefix() + ":ip:" + hash(clientIp);
            retryAfterMillis = store.incrementAndGetRetryAfterMillis(
                    key,
                    settings.maxRequests(),
                    settings.window());
        } catch (DataAccessException | IllegalStateException exception) {
            throw new RateLimitUnavailableException(exception);
        }

        if (retryAfterMillis > 0) {
            throw new RateLimitExceededException(retryAfterMillis);
        }
    }

    private static String hash(String value) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(value.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available", exception);
        }
    }
}
