package com.elsyah.tech.javara.middleware.ratelimit;

import java.time.Duration;
import java.util.UUID;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.stereotype.Component;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.middleware.store.RedisRateLimitStore;

@Component
@ConditionalOnProperty(
        prefix = "app.features",
        name = "rate-limiting",
        havingValue = "true",
        matchIfMissing = true)
@ConditionalOnProperty(
        prefix = "app.features",
        name = "auth-api",
        havingValue = "true",
        matchIfMissing = true)
public class RedisStartupCheck implements SmartInitializingSingleton {
    private static final Duration PROBE_WINDOW = Duration.ofSeconds(1);

    private final RedisConnectionFactory connectionFactory;
    private final RedisRateLimitStore rateLimitStore;
    private final LoginRateLimitProperties loginProperties;
    private final RegisterRateLimitProperties registerProperties;
    private final RefreshRateLimitProperties refreshProperties;
    private final SessionReauthRateLimitProperties sessionReauthProperties;
    private final ApplicationIdentityProperties applicationIdentity;

    public RedisStartupCheck(
            RedisConnectionFactory connectionFactory,
            RedisRateLimitStore rateLimitStore,
            LoginRateLimitProperties loginProperties,
            RegisterRateLimitProperties registerProperties,
            RefreshRateLimitProperties refreshProperties,
            SessionReauthRateLimitProperties sessionReauthProperties,
            ApplicationIdentityProperties applicationIdentity) {
        this.connectionFactory = connectionFactory;
        this.rateLimitStore = rateLimitStore;
        this.loginProperties = loginProperties;
        this.registerProperties = registerProperties;
        this.refreshProperties = refreshProperties;
        this.sessionReauthProperties = sessionReauthProperties;
        this.applicationIdentity = applicationIdentity;
    }

    @Override
    public void afterSingletonsInstantiated() {
        if (!isAnyRateLimitEnabled()) {
            return;
        }
        verifyRedisAvailable();

        String probeKey = "javara:startup-check:" + UUID.randomUUID();
        try {
            rateLimitStore.incrementAndGetRetryAfterMillis(probeKey, 1, PROBE_WINDOW);
        } catch (DataAccessException | IllegalStateException exception) {
            throw new IllegalStateException(
                    "Cannot start " + applicationIdentity.name()
                            + ": Redis rate-limit operations failed their startup check",
                    exception);
        }
    }

    private void verifyRedisAvailable() {
        try (RedisConnection connection = connectionFactory.getConnection()) {
            if (!"PONG".equals(connection.ping())) {
                throw new IllegalStateException(
                        "Cannot start " + applicationIdentity.name()
                                + ": Redis did not respond to PING");
            }
        } catch (DataAccessException exception) {
            throw new IllegalStateException(
                    "Cannot start " + applicationIdentity.name()
                            + ": Redis is required but unavailable",
                    exception);
        }
    }

    private boolean isAnyRateLimitEnabled() {
        return loginProperties.enabled()
                || registerProperties.enabled()
                || refreshProperties.enabled()
                || sessionReauthProperties.enabled();
    }
}
