package com.elsyah.tech.javara.unit.middleware.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import com.elsyah.tech.javara.middleware.security.SessionProperties;

class SessionPropertiesTests {
    @Test
    void defaultsToEnabledIdleExpiryAndSecureSessionLimits() {
        SessionProperties properties = new SessionProperties(null, null, null, null, null);

        assertTrue(properties.idleTimeoutEnabled());
        assertTrue(properties.absoluteLifetimeEnabled());
        assertEquals(Duration.ofMinutes(15), properties.idleTimeout());
        assertEquals(Duration.ofHours(12), properties.absoluteLifetime());
        assertEquals(5, properties.maxConcurrentSessions());
    }

    @Test
    void disabledIdleExpiryAllowsAbsoluteLifetimeToBeConfiguredIndependently() {
        SessionProperties properties = new SessionProperties(
                false, Duration.ofHours(2), true, Duration.ofMinutes(30), 3);

        assertFalse(properties.idleTimeoutEnabled());
        assertEquals(Duration.ofMinutes(30), properties.absoluteLifetime());
    }

    @Test
    void disabledAbsoluteLifetimeUsesRefreshTokenTtlAsLoginBasedSessionLifetime() {
        SessionProperties properties = new SessionProperties(
                false, Duration.ofMinutes(15), false, Duration.ofHours(12), 5);

        assertFalse(properties.absoluteLifetimeEnabled());
        assertEquals(
                Duration.ofDays(30),
                properties.sessionLifetime(Duration.ofDays(30)));
    }

    @Test
    void enabledIdleExpiryRequiresLongerAbsoluteLifetime() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionProperties(
                        true, Duration.ofHours(1), true, Duration.ofHours(1), 5));
    }

    @Test
    void absoluteLifetimeCannotExceedTwelveHours() {
        assertThrows(
                IllegalArgumentException.class,
                () -> new SessionProperties(
                        false, Duration.ofMinutes(15), true, Duration.ofHours(13), 5));
    }

    @Test
    void disabledAbsoluteLifetimeDoesNotValidateUnusedAbsoluteDuration() {
        SessionProperties properties = new SessionProperties(
                false, Duration.ofMinutes(15), false, Duration.ofDays(30), 5);

        assertEquals(
                Duration.ofDays(60),
                properties.sessionLifetime(Duration.ofDays(60)));
    }
}
