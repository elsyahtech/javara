package com.elsyah.tech.javara.middleware.ratelimit;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;

import org.springframework.dao.DataAccessException;
import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.middleware.store.RedisLoginRateLimitStore;

@Component
public class LoginRateLimiter {
    private final RedisLoginRateLimitStore store;
    private final LoginRateLimitProperties properties;

    public LoginRateLimiter(
            RedisLoginRateLimitStore store,
            LoginRateLimitProperties properties) {
        this.store = store;
        this.properties = properties;
    }

    public void checkAllowed(String clientIp) {
        if (clientIp == null || clientIp.isBlank()) {
            throw new IllegalArgumentException("Client IP must not be blank");
        }

        long retryAfterMillis;
        try {
            String key = properties.keyPrefix() + ":ip:" + hash(clientIp);
            retryAfterMillis = store.incrementAndGetRetryAfterMillis(
                    key,
                    properties.maxRequests(),
                    properties.window());
        } catch (DataAccessException | IllegalStateException exception) {
            throw new LoginRateLimitUnavailableException(exception);
        }

        if (retryAfterMillis > 0) {
            throw new LoginRateLimitExceededException(retryAfterMillis);
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
