package com.elsyah.tech.javara.middleware.security;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

@ConfigurationProperties(prefix = "app.security.suspicious-authentication")
public record SuspiciousAuthenticationProperties(
        @DefaultValue("true") boolean enabled,
        @DefaultValue FailureSignal failedLogin,
        @DefaultValue EventSignal newSource,
        @DefaultValue FailureSignal failedMfa,
        @DefaultValue EventSignal securityChange) {
    public enum Action {
        NOTIFY,
        TEMPORARY_LOCK,
        LOCK_AND_NOTIFY
    }

    public record FailureSignal(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("5") int threshold,
            @DefaultValue("10m") Duration window,
            @DefaultValue("15m") Duration lockDuration,
            @DefaultValue("LOCK_AND_NOTIFY") Action action) {
        public FailureSignal {
            if (threshold < 1 || threshold > 1000) {
                throw new IllegalArgumentException(
                        "Suspicious-authentication threshold must be between 1 and 1000");
            }
            validateDuration(window, "window", Duration.ofDays(1));
            validateDuration(lockDuration, "lock-duration", Duration.ofDays(7));
            if (action == null) {
                throw new IllegalArgumentException(
                        "Suspicious-authentication action must be configured");
            }
        }
    }

    public record EventSignal(
            @DefaultValue("true") boolean enabled,
            @DefaultValue("NOTIFY") Action action,
            @DefaultValue("15m") Duration lockDuration) {
        public EventSignal {
            validateDuration(lockDuration, "lock-duration", Duration.ofDays(7));
            if (action == null) {
                throw new IllegalArgumentException(
                        "Suspicious-authentication action must be configured");
            }
        }
    }

    public SuspiciousAuthenticationProperties {
        failedLogin = failedLogin == null ? new FailureSignal(true, 5,
                Duration.ofMinutes(10), Duration.ofMinutes(15), Action.LOCK_AND_NOTIFY) : failedLogin;
        newSource = newSource == null
                ? new EventSignal(true, Action.NOTIFY, Duration.ofMinutes(15))
                : newSource;
        failedMfa = failedMfa == null ? new FailureSignal(true, 5,
                Duration.ofMinutes(10), Duration.ofMinutes(15), Action.LOCK_AND_NOTIFY) : failedMfa;
        securityChange = securityChange == null
                ? new EventSignal(true, Action.NOTIFY, Duration.ofMinutes(15))
                : securityChange;
    }

    private static void validateDuration(
            Duration duration, String name, Duration maximum) {
        if (duration == null
                || duration.isZero()
                || duration.isNegative()
                || duration.compareTo(maximum) > 0) {
            throw new IllegalArgumentException(
                    "Suspicious-authentication " + name
                            + " must be greater than zero and at most " + maximum);
        }
    }
}
