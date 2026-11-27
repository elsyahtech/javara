package com.elsyah.tech.javara.unit.middleware.ratelimit;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.startsWith;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisConnectionFactory;

import com.elsyah.tech.javara.middleware.config.ApplicationIdentityProperties;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RefreshRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RedisStartupCheck;
import com.elsyah.tech.javara.middleware.ratelimit.RegisterRateLimitProperties;
import com.elsyah.tech.javara.middleware.store.RedisRateLimitStore;

class RedisStartupCheckTests {
    @Test
    void verifiesRedisAndProbesRateLimitWhenLimiterIsEnabled() {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisRateLimitStore rateLimitStore = mock(RedisRateLimitStore.class);
        RedisStartupCheck startupCheck = startupCheck(
                connectionFactory,
                rateLimitStore,
                true,
                false,
                false);

        assertDoesNotThrow(startupCheck::afterSingletonsInstantiated);

        verify(connectionFactory).getConnection();
        verify(rateLimitStore).incrementAndGetRetryAfterMillis(
                startsWith("javara:startup-check:"),
                eq(1),
                eq(Duration.ofSeconds(1)));
    }

    @Test
    void verifiesRedisEvenWhenAllAuthenticationRateLimitsAreDisabled() {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisRateLimitStore rateLimitStore = mock(RedisRateLimitStore.class);
        RedisStartupCheck startupCheck = startupCheck(
                connectionFactory,
                rateLimitStore,
                false,
                false,
                false);

        assertDoesNotThrow(startupCheck::afterSingletonsInstantiated);

        verify(connectionFactory).getConnection();
        verifyNoInteractions(rateLimitStore);
    }

    @Test
    void failsStartupWhenRedisIsUnavailableEvenWithAllLimitersDisabled() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisRateLimitStore rateLimitStore = mock(RedisRateLimitStore.class);
        DataAccessResourceFailureException redisFailure =
                new DataAccessResourceFailureException("connection refused");
        when(connectionFactory.getConnection()).thenThrow(redisFailure);
        RedisStartupCheck startupCheck = startupCheck(
                connectionFactory,
                rateLimitStore,
                false,
                false,
                false);

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                startupCheck::afterSingletonsInstantiated);

        assertTrue(exception.getMessage().contains("Redis is required"));
        assertSame(redisFailure, exception.getCause());
        verifyNoInteractions(rateLimitStore);
    }

    @Test
    void failsStartupWhenRedisDoesNotRespondToPing() {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisRateLimitStore rateLimitStore = mock(RedisRateLimitStore.class);
        RedisConnection connection = connectionFactory.getConnection();
        when(connection.ping()).thenReturn(null);
        RedisStartupCheck startupCheck = startupCheck(
                connectionFactory,
                rateLimitStore,
                false,
                false,
                false);

        assertThrows(IllegalStateException.class, startupCheck::afterSingletonsInstantiated);
        verifyNoInteractions(rateLimitStore);
    }

    @Test
    void failsStartupWhenRedisCannotRunRateLimitOperation() {
        RedisConnectionFactory connectionFactory = connectionFactory();
        RedisRateLimitStore rateLimitStore = mock(RedisRateLimitStore.class);
        doThrow(new IllegalStateException("Redis did not return a result"))
                .when(rateLimitStore)
                .incrementAndGetRetryAfterMillis(any(), anyInt(), any(Duration.class));
        RedisStartupCheck startupCheck = startupCheck(
                connectionFactory,
                rateLimitStore,
                false,
                false,
                true);

        assertThrows(IllegalStateException.class, startupCheck::afterSingletonsInstantiated);
    }

    private static RedisConnectionFactory connectionFactory() {
        RedisConnectionFactory connectionFactory = mock(RedisConnectionFactory.class);
        RedisConnection connection = mock(RedisConnection.class);
        when(connection.ping()).thenReturn("PONG");
        when(connectionFactory.getConnection()).thenReturn(connection);
        return connectionFactory;
    }

    private static RedisStartupCheck startupCheck(
            RedisConnectionFactory connectionFactory,
            RedisRateLimitStore rateLimitStore,
            boolean loginEnabled,
            boolean registerEnabled,
            boolean refreshEnabled) {
        return new RedisStartupCheck(
                connectionFactory,
                rateLimitStore,
                new LoginRateLimitProperties(
                        10, Duration.ofMinutes(1), "test:login", loginEnabled),
                new RegisterRateLimitProperties(
                        5, Duration.ofHours(1), "test:register", registerEnabled),
                new RefreshRateLimitProperties(
                        30, Duration.ofMinutes(1), "test:refresh", refreshEnabled),
                new ApplicationIdentityProperties("Test Application"));
    }
}
