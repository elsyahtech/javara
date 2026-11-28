package com.elsyah.tech.javara.middleware.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.security.session")
public record SessionProperties(
        Boolean idleTimeoutEnabled,
        Duration idleTimeout,
        Boolean absoluteLifetimeEnabled,
        Duration absoluteLifetime,
        Integer maxConcurrentSessions) {
    public SessionProperties {
        idleTimeoutEnabled = idleTimeoutEnabled == null || idleTimeoutEnabled;
        absoluteLifetimeEnabled =
                absoluteLifetimeEnabled == null || absoluteLifetimeEnabled;
        idleTimeout = idleTimeout == null ? Duration.ofMinutes(15) : idleTimeout;
        absoluteLifetime = absoluteLifetime == null ? Duration.ofHours(12) : absoluteLifetime;
        maxConcurrentSessions = maxConcurrentSessions == null ? 5 : maxConcurrentSessions;

        if (idleTimeout.isZero() || idleTimeout.isNegative()) {
            throw new IllegalArgumentException("Session idle-timeout must be greater than zero");
        }
        if (absoluteLifetimeEnabled
                && (absoluteLifetime.isZero()
                        || absoluteLifetime.isNegative()
                        || absoluteLifetime.compareTo(Duration.ofHours(12)) > 0
                        || (idleTimeoutEnabled
                                && absoluteLifetime.compareTo(idleTimeout) <= 0))) {
            throw new IllegalArgumentException(
                    "Session absolute-lifetime must be positive, at most 12 hours, "
                            + "and exceed idle-timeout when enabled");
        }
        if (maxConcurrentSessions < 1 || maxConcurrentSessions > 100) {
            throw new IllegalArgumentException(
                    "Session max-concurrent-sessions must be between 1 and 100");
        }
    }

    public Duration sessionLifetime(Duration refreshTokenTtl) {
        return absoluteLifetimeEnabled ? absoluteLifetime : refreshTokenTtl;
    }
}
