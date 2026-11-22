package com.elsyah.tech.javara.unit.middleware.ratelimit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Duration;

import org.junit.jupiter.api.Test;
import org.springframework.data.redis.RedisConnectionFailureException;

import com.elsyah.tech.javara.middleware.ratelimit.AuthRateLimiter;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitExceededException;
import com.elsyah.tech.javara.middleware.ratelimit.LoginRateLimitProperties;
import com.elsyah.tech.javara.middleware.ratelimit.RateLimitUnavailableException;
import com.elsyah.tech.javara.middleware.store.RedisRateLimitStore;

class AuthRateLimiterTests {
    private final RedisRateLimitStore store = mock(RedisRateLimitStore.class);
    private final LoginRateLimitProperties properties = new LoginRateLimitProperties(
            10, Duration.ofMinutes(1), "test:login", true);
    private final AuthRateLimiter limiter = new AuthRateLimiter(store);

    // Verifies that an allowed request is charged against a hashed client IP key.
    @Test
    void allowsRequestsBelowConfiguredLimit() {
        when(store.incrementAndGetRetryAfterMillis(
                anyString(), eq(10), eq(Duration.ofMinutes(1))))
                .thenReturn(0L);

        limiter.checkAllowed("192.0.2.10", properties);

        org.mockito.ArgumentCaptor<String> key = org.mockito.ArgumentCaptor.forClass(String.class);
        verify(store).incrementAndGetRetryAfterMillis(
                key.capture(), eq(10), eq(Duration.ofMinutes(1)));
        assertTrue(key.getValue().startsWith("test:login:ip:"));
        assertFalse(key.getValue().contains("192.0.2.10"));
    }

    // Verifies that a blocked request reports the remaining retry duration in seconds.
    @Test
    void rejectsRequestsAboveConfiguredLimit() {
        when(store.incrementAndGetRetryAfterMillis(
                anyString(), eq(10), eq(Duration.ofMinutes(1))))
                .thenReturn(1501L);

        RateLimitExceededException exception = assertThrows(
                RateLimitExceededException.class,
                () -> limiter.checkAllowed("192.0.2.10", properties));

        assertEquals(2, exception.getRetryAfterSeconds());
    }

    // Verifies that Redis failures reject login rather than silently bypassing the limit.
    @Test
    void rejectsRequestsWhenRedisIsUnavailable() {
        when(store.incrementAndGetRetryAfterMillis(
                anyString(), eq(10), eq(Duration.ofMinutes(1))))
                .thenThrow(new RedisConnectionFailureException("Redis unavailable"));

        assertThrows(
                RateLimitUnavailableException.class,
                () -> limiter.checkAllowed("192.0.2.10", properties));
    }
}
